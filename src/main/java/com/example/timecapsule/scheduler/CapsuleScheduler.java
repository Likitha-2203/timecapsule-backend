package com.example.timecapsule.scheduler;

import com.example.timecapsule.entity.Capsule;
import com.example.timecapsule.repository.CapsuleRepository;
import com.example.timecapsule.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Runs periodically and sends two kinds of email:
 *  1. "opens soon" reminders, and
 *  2. "ready to open" notices once a capsule has unlocked.
 * A flag is only set after the email was sent successfully, so failures are retried on the next run.
 */
@Component
public class CapsuleScheduler {

    private static final Logger log = LoggerFactory.getLogger(CapsuleScheduler.class);

    private final CapsuleRepository capsuleRepository;
    private final NotificationService notificationService;

    public CapsuleScheduler(CapsuleRepository capsuleRepository, NotificationService notificationService) {
        this.capsuleRepository = capsuleRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 30_000)
    public void sendNotifications() {
        Instant now = Instant.now();

        for (Capsule capsule : capsuleRepository.findDueReminders(now)) {
            try {
                if (notificationService.capsuleReminder(capsule)) {
                    capsule.setReminderSent(true);
                    capsuleRepository.save(capsule);
                    log.info("Reminder sent for capsule {}", capsule.getId());
                }
            } catch (Exception e) {
                log.error("Failed to process reminder for capsule {}: {}", capsule.getId(), e.getMessage());
            }
        }

        for (Capsule capsule : capsuleRepository.findByUnlockAtLessThanEqualAndNotifiedFalse(now)) {
            try {
                if (notificationService.capsuleReady(capsule)) {
                    capsule.setNotified(true);
                    capsuleRepository.save(capsule);
                    log.info("Ready notice sent for capsule {}", capsule.getId());
                }
            } catch (Exception e) {
                log.error("Failed to process ready notification for capsule {}: {}", capsule.getId(), e.getMessage());
            }
        }
    }
}
