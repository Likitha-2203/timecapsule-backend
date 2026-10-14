package com.example.timecapsule.service;

import com.example.timecapsule.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final JavaMailSender sender; // null when spring.mail.* is not configured
    private final String from;
    private final String username;
    private final String password;
    private final String brevoApiKey;
    private final String resendApiKey;

    public MailService(ObjectProvider<JavaMailSender> senderProvider,
                       @Value("${app.mail.from:}") String from,
                       @Value("${spring.mail.username:}") String username,
                       @Value("${spring.mail.password:}") String password,
                       @Value("${app.mail.brevo-api-key:${BREVO_API_KEY:}}") String brevoApiKey,
                       @Value("${app.mail.resend-api-key:${RESEND_API_KEY:}}") String resendApiKey) {
        this.sender = senderProvider.getIfAvailable();
        this.from = from;
        this.username = username;
        this.password = password;
        this.brevoApiKey = (brevoApiKey != null) ? brevoApiKey.trim() : "";
        this.resendApiKey = (resendApiKey != null) ? resendApiKey.trim() : "";
    }

    /** Returns true if email sending is configured with HTTP API or SMTP credentials. */
    public boolean isConfigured() {
        if (!brevoApiKey.isBlank() && !brevoApiKey.equals("null")) {
            return true;
        }
        if (!resendApiKey.isBlank() && !resendApiKey.equals("null")) {
            return true;
        }
        return sender != null
                && username != null && !username.isBlank()
                && password != null && !password.isBlank()
                && !password.equals("null");
    }

    /** Like send(), but never throws: returns false if the email could not be sent. */
    public boolean trySend(String to, String subject, String body) {
        try {
            send(to, subject, body);
            return true;
        } catch (ApiException e) {
            return false; // already logged inside send()
        }
    }

    public void send(String to, String subject, String body) {
        if (!isConfigured()) {
            // Development or unconfigured mode: email credentials not provided, log email instead of failing
            log.info("\n===== [EMAIL NOT CONFIGURED / DEV MODE] =====\nTo: {}\nSubject: {}\n\n{}\n=================================================",
                    to, subject, body);
            return;
        }

        // 1. Prefer Brevo HTTP API over port 443 (Allowed by cloud providers like Render free tier)
        if (!brevoApiKey.isBlank() && !brevoApiKey.equals("null")) {
            sendViaBrevo(to, subject, body);
            return;
        }

        // 2. Prefer Resend HTTP API over port 443
        if (!resendApiKey.isBlank() && !resendApiKey.equals("null")) {
            sendViaResend(to, subject, body);
            return;
        }

        // 3. Fallback to JavaMailSender SMTP (port 465 SSL)
        sendViaSmtp(to, subject, body);
    }

    private void sendViaBrevo(String to, String subject, String body) {
        try {
            String senderEmail = (username != null && !username.isBlank()) ? username : "timecapsuleoffic@gmail.com";
            String jsonPayload = "{"
                    + "\"sender\":{\"name\":\"TimeCapsule\",\"email\":\"" + escapeJson(senderEmail) + "\"},"
                    + "\"to\":[{\"email\":\"" + escapeJson(to) + "\"}],"
                    + "\"subject\":\"" + escapeJson(subject) + "\","
                    + "\"textContent\":\"" + escapeJson(body) + "\""
                    + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.brevo.com/v3/smtp/email"))
                    .timeout(Duration.ofSeconds(15))
                    .header("api-key", brevoApiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Email sent successfully via Brevo HTTPS API to {}", to);
            } else {
                log.error("Brevo API error (HTTP {}): {}", response.statusCode(), response.body());
                throw new RuntimeException("Brevo API HTTP " + response.statusCode() + ": " + response.body());
            }
        } catch (Exception e) {
            log.error("Failed to send email via Brevo to {}: {}", to, e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "We could not send the email right now. Please try again later.");
        }
    }

    private void sendViaResend(String to, String subject, String body) {
        try {
            String fromAddress = (from != null && !from.isBlank()) ? from : "TimeCapsule <onboarding@resend.dev>";
            String jsonPayload = "{"
                    + "\"from\":\"" + escapeJson(fromAddress) + "\","
                    + "\"to\":[\"" + escapeJson(to) + "\"],"
                    + "\"subject\":\"" + escapeJson(subject) + "\","
                    + "\"text\":\"" + escapeJson(body) + "\""
                    + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.resend.com/emails"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + resendApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Email sent successfully via Resend HTTPS API to {}", to);
            } else {
                log.error("Resend API error (HTTP {}): {}", response.statusCode(), response.body());
                throw new RuntimeException("Resend API HTTP " + response.statusCode() + ": " + response.body());
            }
        } catch (Exception e) {
            log.error("Failed to send email via Resend to {}: {}", to, e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "We could not send the email right now. Please try again later.");
        }
    }

    private void sendViaSmtp(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (from != null && !from.isBlank()) {
                message.setFrom(from);
            } else if (username != null && !username.isBlank()) {
                message.setFrom(username);
            }
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
            log.info("Email sent successfully via SMTP to {}", to);
        } catch (Exception e) {
            log.error("Failed to send email via SMTP to {}: {}", to, e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "We could not send the email right now. Please try again later.");
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}