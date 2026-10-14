package com.example.timecapsule.controller;

import com.example.timecapsule.repository.UserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping({"/api/users", "/users"})
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Lets the "create capsule" form confirm that a recipient has a verified account. */
    @GetMapping("/lookup")
    public Map<String, Object> lookup(@RequestParam String email) {
        return userRepository.findByEmail(email.trim().toLowerCase())
                .filter(u -> u.isVerified())
                .<Map<String, Object>>map(u -> Map.of("exists", true, "name", u.getName()))
                .orElse(Map.of("exists", false));
    }
}