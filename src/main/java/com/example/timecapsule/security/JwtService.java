package com.example.timecapsule.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(@Value("${app.jwt.secret:}") String secret,
                      @Value("${app.jwt.expiration-minutes:10080}") long expirationMinutes) {
        this.key = buildKey(secret);
        this.expirationMs = expirationMinutes * 60 * 1000;
    }

    private static SecretKey buildKey(String secret) {
        String effectiveSecret = (secret != null && !secret.isBlank())
                ? secret
                : "timecapsule-secure-jwt-signing-key-fallback-for-local-and-test-environments";
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                    .digest(effectiveSecret.getBytes(StandardCharsets.UTF_8));
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    public String generateToken(String email) {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    public String extractEmail(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}