package com.example.timecapsule.service;

import com.example.timecapsule.dto.Dtos.CapsuleListResponse;
import com.example.timecapsule.dto.Dtos.CapsuleRequest;
import com.example.timecapsule.dto.Dtos.OpenedCapsule;
import com.example.timecapsule.dto.Dtos.ReceivedItem;
import com.example.timecapsule.dto.Dtos.SentItem;
import com.example.timecapsule.entity.Capsule;
import com.example.timecapsule.entity.User;
import com.example.timecapsule.exception.ApiException;
import com.example.timecapsule.repository.CapsuleRepository;
import com.example.timecapsule.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class CapsuleService {

    private final CapsuleRepository capsuleRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final long reminderHours;

    public CapsuleService(CapsuleRepository capsuleRepository,
                          UserRepository userRepository,
                          NotificationService notificationService,
                          @Value("${app.capsule.reminder-hours:24}") long reminderHours) {
        this.capsuleRepository = capsuleRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.reminderHours = reminderHours;
    }

    public Long create(String senderEmail, CapsuleRequest request) {
        User sender = currentUser(senderEmail);

        if (!request.unlockAt().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The unlock time must be in the future.");
        }

        User recipient = sender;
        String target = request.recipientEmail() == null ? "" : request.recipientEmail().trim().toLowerCase();

        if (!target.isEmpty() && !target.equals(sender.getEmail())) {
            recipient = userRepository.findByEmail(target)
                    .filter(User::isVerified)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                            "No verified TimeCapsule account exists for that email address."));
        }

        Capsule capsule = new Capsule();
        capsule.setTitle(request.title().trim());
        capsule.setMood(request.mood() == null || request.mood().isBlank() ? "💌" : request.mood());
        capsule.setMessage(request.message());
        capsule.setUnlockAt(request.unlockAt());
        capsule.setCreatedAt(Instant.now());
        capsule.setSender(sender);
        capsule.setRecipient(recipient);

        // The "opens soon" reminder goes out reminderHours before unlocking. For short capsules the
        // reminder is sent halfway through instead, so it never arrives right after creation.
        long totalSeconds = Duration.between(Instant.now(), request.unlockAt()).getSeconds();
        long windowSeconds = Math.min(reminderHours * 3600L, totalSeconds / 2);
        capsule.setReminderAt(request.unlockAt().minusSeconds(windowSeconds));

        Capsule saved = capsuleRepository.save(capsule);

        // Tell the recipient straight away. A mail failure must never stop the capsule from being created.
        notificationService.capsuleCreated(saved);

        return saved.getId();
    }

    /** Lists never include the message: it is only revealed by open() after the unlock time. */
    @Transactional(readOnly = true)
    public CapsuleListResponse list(String email) {
        User user = currentUser(email);

        List<ReceivedItem> received = capsuleRepository.findByRecipientOrderByUnlockAtAsc(user).stream()
                .map(c -> new ReceivedItem(c.getId(), c.getTitle(), c.getMood(), c.getUnlockAt(),
                        statusOf(c), c.getSender().getId().equals(user.getId()), c.getSender().getName()))
                .toList();

        List<SentItem> sent = capsuleRepository.findSentToOthers(user).stream()
                .map(c -> new SentItem(c.getId(), c.getTitle(), c.getMood(), c.getUnlockAt(),
                        statusOf(c), c.getRecipient().getName()))
                .toList();

        return new CapsuleListResponse(received, sent);
    }

    public OpenedCapsule open(String email, Long id) {
        User user = currentUser(email);
        Capsule capsule = capsuleRepository.findById(id)
                .filter(c -> c.getRecipient().getId().equals(user.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Capsule not found."));

        if (Instant.now().isBefore(capsule.getUnlockAt())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This capsule is still locked.");
        }

        if (!capsule.isOpened()) {
            capsule.setOpened(true);
            capsule.setOpenedAt(Instant.now());
        }

        return new OpenedCapsule(capsule.getId(), capsule.getTitle(), capsule.getMood(), capsule.getMessage(),
                capsule.getSender().getId().equals(user.getId()), capsule.getSender().getName());
    }

    public void delete(String email, Long id) {
        User user = currentUser(email);
        Capsule capsule = capsuleRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Capsule not found."));

        boolean isRecipient = capsule.getRecipient().getId().equals(user.getId());
        boolean isSender = capsule.getSender().getId().equals(user.getId());

        // The recipient can delete at any time. The sender can only cancel before it unlocks.
        if (!isRecipient && !(isSender && statusOf(capsule).equals("locked"))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot delete this capsule.");
        }
        capsuleRepository.delete(capsule);
    }

    private User currentUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Please log in again."));
    }

    private static String statusOf(Capsule c) {
        if (c.isOpened()) return "opened";
        return Instant.now().isBefore(c.getUnlockAt()) ? "locked" : "ready";
    }
}