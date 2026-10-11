package com.example.timecapsule.service;

import com.example.timecapsule.entity.Capsule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Builds and sends every capsule-related email: created, opens soon, ready to open. */
@Service
public class NotificationService {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a (zzz)", Locale.ENGLISH);

    private final MailService mailService;
    private final ZoneId zone;
    private final String frontendUrl;

    public NotificationService(MailService mailService,
                               @Value("${app.timezone:Asia/Kolkata}") String timezone,
                               @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.mailService = mailService;
        this.zone = ZoneId.of(timezone);
        this.frontendUrl = frontendUrl;
    }

    /** Sent to the recipient the moment a capsule is sealed. */
    public boolean capsuleCreated(Capsule c) {
        String to = c.getRecipient().getEmail();
        String name = c.getRecipient().getName();

        if (isFromSelf(c)) {
            return mailService.trySend(to, "🔒 Your TimeCapsule is sealed",
                    "Hi " + name + ",\n\n"
                            + "Your capsule \"" + c.getTitle() + "\" is sealed and safely stored.\n"
                            + "It will unlock on " + when(c.getUnlockAt()) + ".\n\n"
                            + "We will remind you shortly before it opens, and again the moment it is ready.\n\n"
                            + footer());
        }
        return mailService.trySend(to, "💌 " + c.getSender().getName() + " sent you a TimeCapsule",
                "Hi " + name + ",\n\n"
                        + c.getSender().getName() + " has sealed a TimeCapsule for you: \"" + c.getTitle() + "\".\n\n"
                        + "It is locked until " + when(c.getUnlockAt()) + ". Nobody can read it before then.\n"
                        + "We will remind you shortly before it opens, and again the moment it is ready.\n\n"
                        + footer());
    }

    /** Sent shortly before a capsule unlocks. */
    public boolean capsuleReminder(Capsule c) {
        Duration left = Duration.between(Instant.now(), c.getUnlockAt());
        return mailService.trySend(c.getRecipient().getEmail(), "⏳ Your TimeCapsule opens soon",
                "Hi " + c.getRecipient().getName() + ",\n\n"
                        + "The capsule \"" + c.getTitle() + "\"" + from(c)
                        + " unlocks in " + remaining(left) + " (" + when(c.getUnlockAt()) + ").\n\n"
                        + "Get ready to open it!\n\n"
                        + footer());
    }

    /** Sent when a capsule has unlocked. */
    public boolean capsuleReady(Capsule c) {
        return mailService.trySend(c.getRecipient().getEmail(), "🎉 Your TimeCapsule is ready to open",
                "Hi " + c.getRecipient().getName() + ",\n\n"
                        + "The capsule \"" + c.getTitle() + "\"" + from(c) + " is now unlocked and ready to read.\n\n"
                        + footer());
    }

    // ---------- helpers ----------

    private static boolean isFromSelf(Capsule c) {
        return c.getSender().getId().equals(c.getRecipient().getId());
    }

    private static String from(Capsule c) {
        return isFromSelf(c) ? "" : " from " + c.getSender().getName();
    }

    private String when(Instant instant) {
        return DATE_FORMAT.format(instant.atZone(zone));
    }

    private String footer() {
        return "Open TimeCapsule: " + frontendUrl + "\n\n— TimeCapsule";
    }

    private static String remaining(Duration d) {
        long minutes = Math.max(1, Math.round(d.getSeconds() / 60.0));
        if (minutes < 60) return "about " + minutes + (minutes == 1 ? " minute" : " minutes");
        long hours = Math.round(minutes / 60.0);
        if (hours < 48) return "about " + hours + (hours == 1 ? " hour" : " hours");
        return "about " + Math.round(hours / 24.0) + " days";
    }
}