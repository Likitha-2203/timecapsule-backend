package com.example.timecapsule.entity;

import jakarta.persistence.*;

import java.time.Instant;

/** One-time code used for email verification and password reset. Only a hash is stored. */
@Entity
@Table(name = "otp_tokens", indexes = @Index(columnList = "email, purpose"))
public class OtpToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 20)
    private String purpose;

    @Column(nullable = false)
    private String codeHash;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts = 0;

    public OtpToken() {
    }

    public OtpToken(String email, String purpose, String codeHash, Instant expiresAt) {
        this.email = email;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
    }

    public String getCodeHash() { return codeHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
}