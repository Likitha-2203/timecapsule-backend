package com.example.timecapsule.controller;

import com.example.timecapsule.dto.Dtos.CapsuleListResponse;
import com.example.timecapsule.dto.Dtos.CapsuleRequest;
import com.example.timecapsule.dto.Dtos.OpenedCapsule;
import com.example.timecapsule.service.CapsuleService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/capsules")
public class CapsuleController {

    private final CapsuleService capsuleService;

    public CapsuleController(CapsuleService capsuleService) {
        this.capsuleService = capsuleService;
    }

    @PostMapping
    public Map<String, Object> create(@Valid @RequestBody CapsuleRequest request, Authentication auth) {
        Long id = capsuleService.create(auth.getName(), request);
        return Map.of("ok", true, "id", id);
    }

    @GetMapping
    public CapsuleListResponse list(Authentication auth) {
        return capsuleService.list(auth.getName());
    }

    @PostMapping("/{id}/open")
    public OpenedCapsule open(@PathVariable Long id, Authentication auth) {
        return capsuleService.open(auth.getName(), id);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id, Authentication auth) {
        capsuleService.delete(auth.getName(), id);
        return Map.of("ok", true);
    }
}