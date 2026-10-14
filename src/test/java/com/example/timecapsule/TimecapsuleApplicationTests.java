package com.example.timecapsule;

import com.example.timecapsule.dto.Dtos.CapsuleRequest;
import com.example.timecapsule.dto.Dtos.LoginRequest;
import com.example.timecapsule.dto.Dtos.RegisterRequest;
import com.example.timecapsule.dto.Dtos.VerifyRequest;
import com.example.timecapsule.entity.Capsule;
import com.example.timecapsule.entity.OtpToken;
import com.example.timecapsule.entity.User;
import com.example.timecapsule.repository.CapsuleRepository;
import com.example.timecapsule.repository.OtpTokenRepository;
import com.example.timecapsule.repository.UserRepository;
import com.example.timecapsule.security.JwtService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class TimecapsuleApplicationTests {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpTokenRepository otpTokenRepository;

    @Autowired
    private CapsuleRepository capsuleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        capsuleRepository.deleteAll();
        otpTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Health endpoint returns UP")
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("User registration creates unverified user and OTP")
    void testUserRegistration() throws Exception {
        RegisterRequest req = new RegisterRequest("Alice Test", "alice@example.com", "Password123!");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.email").value("alice@example.com"));

        User user = userRepository.findByEmail("alice@example.com").orElse(null);
        assertThat(user).isNotNull();
        assertThat(user.isVerified()).isFalse();
        assertThat(passwordEncoder.matches("Password123!", user.getPassword())).isTrue();

        OtpToken otp = otpTokenRepository.findByEmailAndPurpose("alice@example.com", "VERIFY").orElse(null);
        assertThat(otp).isNotNull();
    }

    @Test
    @DisplayName("Registration rejects weak password")
    void testRegistrationWeakPassword() throws Exception {
        RegisterRequest req = new RegisterRequest("Weak User", "weak@example.com", "12345");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("Email verification completes registration")
    void testEmailVerification() throws Exception {
        User user = new User();
        user.setName("Bob Test");
        user.setEmail("bob@example.com");
        user.setPassword(passwordEncoder.encode("Secret@123"));
        user.setVerified(false);
        userRepository.save(user);

        String code = "123456";
        String codeHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));
        otpTokenRepository.save(new OtpToken("bob@example.com", "VERIFY", codeHash, Instant.now().plus(10, ChronoUnit.MINUTES)));

        VerifyRequest verifyReq = new VerifyRequest("bob@example.com", code);
        mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.user.email").value("bob@example.com"));

        User verified = userRepository.findByEmail("bob@example.com").orElseThrow();
        assertThat(verified.isVerified()).isTrue();
    }

    @Test
    @DisplayName("Login with valid credentials returns JWT")
    void testLoginSuccess() throws Exception {
        User user = new User();
        user.setName("Charlie Test");
        user.setEmail("charlie@example.com");
        user.setPassword(passwordEncoder.encode("ValidPass@1"));
        user.setVerified(true);
        userRepository.save(user);

        LoginRequest loginReq = new LoginRequest("charlie@example.com", "ValidPass@1");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.user.name").value("Charlie Test"));
    }

    @Test
    @DisplayName("Login with invalid password returns 401")
    void testLoginInvalidPassword() throws Exception {
        User user = new User();
        user.setName("David Test");
        user.setEmail("david@example.com");
        user.setPassword(passwordEncoder.encode("ValidPass@1"));
        user.setVerified(true);
        userRepository.save(user);

        LoginRequest loginReq = new LoginRequest("david@example.com", "WrongPassword@1");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Incorrect email or password."));
    }

    @Test
    @DisplayName("Login for unverified user returns 403 with needsVerification")
    void testLoginUnverified() throws Exception {
        User user = new User();
        user.setName("Eve Test");
        user.setEmail("eve@example.com");
        user.setPassword(passwordEncoder.encode("ValidPass@1"));
        user.setVerified(false);
        userRepository.save(user);

        LoginRequest loginReq = new LoginRequest("eve@example.com", "ValidPass@1");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.needsVerification").value(true));
    }

    @Test
    @DisplayName("Protected endpoint access without JWT returns 401")
    void testProtectedEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/capsules"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Protected endpoint access with invalid JWT returns 401")
    void testProtectedEndpointWithInvalidToken() throws Exception {
        mockMvc.perform(get("/api/capsules")
                        .header("Authorization", "Bearer invalid-tampered-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Protected endpoint access with valid JWT returns 200")
    void testProtectedEndpointWithValidToken() throws Exception {
        User user = new User();
        user.setName("Frank Test");
        user.setEmail("frank@example.com");
        user.setPassword(passwordEncoder.encode("ValidPass@1"));
        user.setVerified(true);
        userRepository.save(user);

        String token = jwtService.generateToken("frank@example.com");

        mockMvc.perform(get("/api/capsules")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").isArray())
                .andExpect(jsonPath("$.sent").isArray());
    }

    @Test
    @DisplayName("Capsule creation, locked privacy, and unlock lifecycle")
    void testCapsuleLifecycle() throws Exception {
        User sender = new User();
        sender.setName("Sender User");
        sender.setEmail("sender@example.com");
        sender.setPassword(passwordEncoder.encode("ValidPass@1"));
        sender.setVerified(true);
        userRepository.save(sender);

        User recipient = new User();
        recipient.setName("Recipient User");
        recipient.setEmail("recipient@example.com");
        recipient.setPassword(passwordEncoder.encode("ValidPass@1"));
        recipient.setVerified(true);
        userRepository.save(recipient);

        String senderToken = jwtService.generateToken("sender@example.com");
        String recipientToken = jwtService.generateToken("recipient@example.com");

        // 1. Create capsule scheduled for future
        Instant futureUnlock = Instant.now().plus(7, ChronoUnit.DAYS);
        CapsuleRequest createReq = new CapsuleRequest(
                "Birthday Surprise",
                "🎉",
                "Super secret future message content!",
                futureUnlock,
                "recipient@example.com"
        );

        MvcResult createResult = mockMvc.perform(post("/api/capsules")
                        .header("Authorization", "Bearer " + senderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();

        Long capsuleId = capsuleRepository.findAll().get(0).getId();

        // 2. Listing capsules for recipient should NOT reveal message content
        mockMvc.perform(get("/api/capsules")
                        .header("Authorization", "Bearer " + recipientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received[0].title").value("Birthday Surprise"))
                .andExpect(jsonPath("$.received[0].message").doesNotExist()); // message is protected!

        // 3. Opening locked capsule before unlock time returns 403 Forbidden
        mockMvc.perform(post("/api/capsules/" + capsuleId + "/open")
                        .header("Authorization", "Bearer " + recipientToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("This capsule is still locked."));

        // 4. Another unauthorized user cannot open recipient's capsule (returns 404)
        mockMvc.perform(post("/api/capsules/" + capsuleId + "/open")
                        .header("Authorization", "Bearer " + senderToken))
                .andExpect(status().isNotFound());

        // 5. Simulate unlock time reached
        Capsule storedCapsule = capsuleRepository.findById(capsuleId).orElseThrow();
        storedCapsule.setUnlockAt(Instant.now().minus(1, ChronoUnit.HOURS));
        capsuleRepository.save(storedCapsule);

        // 6. Authorized recipient opens unlocked capsule -> message is revealed
        mockMvc.perform(post("/api/capsules/" + capsuleId + "/open")
                        .header("Authorization", "Bearer " + recipientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Super secret future message content!"))
                .andExpect(jsonPath("$.title").value("Birthday Surprise"));
    }

    @Test
    @DisplayName("Capsule creation rejects past unlock time")
    void testCapsulePastUnlockTimeRejected() throws Exception {
        User user = new User();
        user.setName("Grace Test");
        user.setEmail("grace@example.com");
        user.setPassword(passwordEncoder.encode("ValidPass@1"));
        user.setVerified(true);
        userRepository.save(user);

        String token = jwtService.generateToken("grace@example.com");

        Instant pastUnlock = Instant.now().minus(1, ChronoUnit.DAYS);
        CapsuleRequest createReq = new CapsuleRequest(
                "Past Capsule",
                "💌",
                "Should not be allowed",
                pastUnlock,
                null
        );

        mockMvc.perform(post("/api/capsules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("The unlock time must be in the future."));
    }
}
