package com.example.timecapsule.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class Dtos {

private Dtos() {
}

    // ----- requests -----
public record RegisterRequest(@NotBlank String name, @NotBlank @Email String email, @NotBlank String password) {}

public record EmailRequest(@NotBlank @Email String email) {}

public record VerifyRequest(@NotBlank String email, @NotBlank String code) {}

public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

public record ResetRequest(@NotBlank String email, @NotBlank String code, @NotBlank String password) {}

public record CapsuleRequest(
        @NotBlank @Size(max = 40) String title,
        String mood,
        @NotBlank @Size(max = 65536) String message,
        @NotNull Instant unlockAt,
        String recipientEmail) {}

    // ----- responses -----
public record UserInfo(String name, String email) {}

public record AuthResponse(String token, UserInfo user) {}

public record ReceivedItem(Long id, String title, String mood, Instant unlockAt, String status,
                boolean fromSelf, String senderName) {}

public record SentItem(Long id, String title, String mood, Instant unlockAt, String status,
                String recipientName) {}

public record CapsuleListResponse(List<ReceivedItem> received, List<SentItem> sent) {}

public record OpenedCapsule(Long id, String title, String mood, String message,
                                boolean fromSelf, String senderName) {}
}