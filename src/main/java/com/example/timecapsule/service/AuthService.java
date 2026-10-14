package com.example.timecapsule.service;

import com.example.timecapsule.dto.Dtos.AuthResponse;
import com.example.timecapsule.dto.Dtos.RegisterRequest;
import com.example.timecapsule.dto.Dtos.UserInfo;
import com.example.timecapsule.entity.LoginHistory;
import com.example.timecapsule.entity.OtpToken;
import com.example.timecapsule.entity.User;
import com.example.timecapsule.exception.ApiException;
import com.example.timecapsule.repository.LoginHistoryRepository;
import com.example.timecapsule.repository.OtpTokenRepository;
import com.example.timecapsule.repository.UserRepository;
import com.example.timecapsule.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Pattern;

// noRollbackFor: failed code attempts must still be saved, otherwise the attempt limit would not work
@Service
@Transactional(noRollbackFor = ApiException.class)
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String VERIFY = "VERIFY";
    private static final String RESET = "RESET";
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern SPECIAL = Pattern.compile("[^A-Za-z0-9\\s]");

    private final UserRepository userRepository;
    private final OtpTokenRepository otpRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MailService mailService;

    public AuthService(UserRepository userRepository,
                       OtpTokenRepository otpRepository,
                       LoginHistoryRepository loginHistoryRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       MailService mailService) {
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.loginHistoryRepository = loginHistoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.mailService = mailService;
    }

    public String register(RegisterRequest request) {
        requireStrongPassword(request.password());

        String email = norm(request.email());
        User user = userRepository.findByEmail(email).orElse(null);

        if (user != null && user.isVerified()) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }
        if (user == null) {
            user = new User();
            user.setEmail(email);
        }

        // An unverified account that registers again simply refreshes its details
        user.setName(request.name().trim());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setVerified(false);
        userRepository.save(user);

        sendCode(user, VERIFY, "Verify your TimeCapsule email",
                "Welcome to TimeCapsule! Use this code to verify your email address.");
        return email;
    }

    public void resend(String rawEmail) {
        userRepository.findByEmail(norm(rawEmail))
                .filter(u -> !u.isVerified())
                .ifPresent(u -> sendCode(u, VERIFY, "Your TimeCapsule verification code",
                        "Here is your new verification code."));
    }

    public AuthResponse verify(String rawEmail, String code) {
        User user = userRepository.findByEmail(norm(rawEmail))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Account not found."));

        if (!user.isVerified()) {
            checkCode(user.getEmail(), VERIFY, code);
            user.setVerified(true);
            userRepository.save(user);
        }
        recordLogin(user);
        return authResponse(user);
    }

    public AuthResponse login(String rawEmail, String password) {
        User user = userRepository.findByEmail(norm(rawEmail)).orElse(null);

        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Incorrect email or password.");
        }

        if (!user.isVerified()) {
            sendCode(user, VERIFY, "Your TimeCapsule verification code",
                    "Please verify your email to continue.");
            throw new ApiException(HttpStatus.FORBIDDEN, "Please verify your email first.",
                    Map.of("needsVerification", true, "email", user.getEmail()));
        }
        recordLogin(user);
        return authResponse(user);
    }

    public void forgotPassword(String rawEmail) {
        // The caller always gets the same response, so nobody can discover which emails are registered
        userRepository.findByEmail(norm(rawEmail))
                .filter(User::isVerified)
                .ifPresent(u -> sendCode(u, RESET, "Reset your TimeCapsule password",
                        "We received a request to reset your password."));
    }

    public void resetPassword(String rawEmail, String code, String newPassword) {
        User user = userRepository.findByEmail(norm(rawEmail))
                .filter(User::isVerified)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Incorrect code."));

        requireStrongPassword(newPassword);
        checkCode(user.getEmail(), RESET, code);

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public void testMail(String to) {
        mailService.send(to.trim(), "TimeCapsule Delivery Test",
                "Hello from TimeCapsule!\n\nIf you are reading this email, real email delivery to your inbox is working 100%!\n\n— TimeCapsule Team");
    }

    // ---------- helpers ----------

    /** Every successful login is stored in the login_history table. */
    private void recordLogin(User user) {
        LoginHistory entry = new LoginHistory();
        entry.setUsername(user.getName());
        entry.setEmail(user.getEmail());
        entry.setLoginAt(LocalDateTime.now());
        loginHistoryRepository.save(entry);
    }

    private AuthResponse authResponse(User user) {
        return new AuthResponse(jwtService.generateToken(user.getEmail()),
                new UserInfo(user.getName(), user.getEmail()));
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    private static void requireStrongPassword(String password) {
        if (password == null || password.length() < 6 || !SPECIAL.matcher(password).find()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Password must be at least 6 characters and include a special symbol.");
        }
    }

    private void sendCode(User user, String purpose, String subject, String intro) {
        String code = String.format("%06d", 100000 + RANDOM.nextInt(900000));

        otpRepository.deleteByEmailAndPurpose(user.getEmail(), purpose);
        otpRepository.flush();
        otpRepository.save(new OtpToken(user.getEmail(), purpose, hash(code), Instant.now().plus(CODE_TTL)));

        boolean sent = mailService.trySend(user.getEmail(), subject,
                "Hi " + user.getName() + ",\n\n" + intro + "\n\nYour code: " + code
                        + "\n\nThis code expires in 10 minutes. If you did not request it, you can ignore this email."
                        + "\n\n— TimeCapsule");

        if (!sent) {
            log.warn("\n=======================================================\n[EMAIL DELIVERY NOTICE]\nCould not deliver email to {}.\nYour 6-digit verification code is: {}\n=======================================================",
                    user.getEmail(), code);
        }
    }

    private void checkCode(String email, String purpose, String code) {
        OtpToken token = otpRepository.findByEmailAndPurpose(email, purpose)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                        "No active code. Please request a new one."));

        if (Instant.now().isAfter(token.getExpiresAt())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This code has expired. Please request a new one.");
        }
        if (token.getAttempts() >= MAX_ATTEMPTS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Too many attempts. Please request a new code.");
        }

        token.setAttempts(token.getAttempts() + 1);
        String given = hash(code == null ? "" : code.trim());

        if (!MessageDigest.isEqual(token.getCodeHash().getBytes(StandardCharsets.UTF_8),
                given.getBytes(StandardCharsets.UTF_8))) {
            otpRepository.save(token);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Incorrect code.");
        }
        otpRepository.delete(token); // a code can only be used once
    }

    private static String hash(String code) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
