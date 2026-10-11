package com.example.timecapsule.scheduler;

import com.example.timecapsule.entity.Capsule;
import com.example.timecapsule.repository.CapsuleRepository;
import com.example.timecapsule.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Runs every 30 seconds and sends two kinds of email:
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

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void sendNotifications() {
        Instant now = Instant.now();

        for (Capsule capsule : capsuleRepository.findDueReminders(now)) {
            if (notificationService.capsuleReminder(capsule)) {
                capsule.setReminderSent(true);
                log.info("Reminder sent for capsule {}", capsule.getId());
            }
        }

        for (Capsule capsule : capsuleRepository.findByUnlockAtLessThanEqualAndNotifiedFalse(now)) {
            if (notificationService.capsuleReady(capsule)) {
                capsule.setNotified(true);
                log.info("Ready notice sent for capsule {}", capsule.getId());
            }
        }
    }
}
