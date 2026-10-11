package com.example.timecapsule.repository;

import com.example.timecapsule.entity.OtpToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpTokenRepository extends JpaRepository<OtpToken, Long> {

    Optional<OtpToken> findByEmailAndPurpose(String email, String purpose);

    void deleteByEmailAndPurpose(String email, String purpose);
}