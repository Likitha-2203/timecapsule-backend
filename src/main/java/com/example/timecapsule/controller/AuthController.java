package com.example.timecapsule.controller;

import com.example.timecapsule.dto.Dtos.AuthResponse;
import com.example.timecapsule.dto.Dtos.EmailRequest;
import com.example.timecapsule.dto.Dtos.LoginRequest;
import com.example.timecapsule.dto.Dtos.RegisterRequest;
import com.example.timecapsule.dto.Dtos.ResetRequest;
import com.example.timecapsule.dto.Dtos.VerifyRequest;
import com.example.timecapsule.service.AuthService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({"/api/auth", "/auth"})
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Map<String, Object> register(
            @Valid @RequestBody RegisterRequest request) {

        String email = authService.register(request);

        return Map.of(
                "ok", true,
                "email", email
        );
    }

    @PostMapping("/resend")
    public Map<String, Object> resend(
            @Valid @RequestBody EmailRequest request) {

        authService.resend(request.email());

        return Map.of("ok", true);
    }

    @PostMapping("/verify")
    public AuthResponse verify(
            @Valid @RequestBody VerifyRequest request) {

        return authService.verify(
                request.email(),
                request.code()
        );
    }

    @PostMapping("/login")
    public AuthResponse login(
            @Valid @RequestBody LoginRequest request) {

        return authService.login(
                request.email(),
                request.password()
        );
    }

    @PostMapping("/forgot")
    public Map<String, Object> forgot(
            @Valid @RequestBody EmailRequest request) {

        authService.forgotPassword(request.email());

        return Map.of("ok", true);
    }

    @PostMapping("/reset")
    public Map<String, Object> reset(
            @Valid @RequestBody ResetRequest request) {

        authService.resetPassword(
                request.email(),
                request.code(),
                request.password()
        );

        return Map.of("ok", true);
    }

    @GetMapping("/test-mail")
    public Map<String, Object> testMail(@RequestParam String to) {
        authService.testMail(to);
        return Map.of("ok", true, "message", "Email sent successfully to " + to);
    }
}