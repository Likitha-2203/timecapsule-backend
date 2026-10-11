package com.example.timecapsule.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "capsules")
public class Capsule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String title;

    @Column(nullable = false, length = 16)
    private String mood;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String message;

    @Column(nullable = false)
    private Instant unlockAt;

    @Column(nullable = false)
    private Instant createdAt;

    @ManyToOne(optional = false)
    private User sender;

    @ManyToOne(optional = false)
    private User recipient;

    @Column(nullable = false)
    private boolean opened = false;

    private Instant openedAt;

    /** True once the "your capsule is ready" email has been sent. */
    @Column(nullable = false)
    private boolean notified = false;

    /** When the "opens soon" reminder email should be sent. */
    private Instant reminderAt;

    /** True once the "opens soon" reminder email has been sent. */
    @Column(nullable = false)
    private boolean reminderSent = false;

    public Capsule() {
    }

    public Long getId() { return id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMood() { return mood; }
    public void setMood(String mood) { this.mood = mood; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Instant getUnlockAt() { return unlockAt; }
    public void setUnlockAt(Instant unlockAt) { this.unlockAt = unlockAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public User getSender() { return sender; }
    public void setSender(User sender) { this.sender = sender; }

    public User getRecipient() { return recipient; }
    public void setRecipient(User recipient) { this.recipient = recipient; }

    public boolean isOpened() { return opened; }
    public void setOpened(boolean opened) { this.opened = opened; }

    public Instant getOpenedAt() { return openedAt; }
    public void setOpenedAt(Instant openedAt) { this.openedAt = openedAt; }

    public boolean isNotified() { return notified; }
    public void setNotified(boolean notified) { this.notified = notified; }

    public Instant getReminderAt() { return reminderAt; }
    public void setReminderAt(Instant reminderAt) { this.reminderAt = reminderAt; }

    public boolean isReminderSent() { return reminderSent; }
    public void setReminderSent(boolean reminderSent) { this.reminderSent = reminderSent; }
}
