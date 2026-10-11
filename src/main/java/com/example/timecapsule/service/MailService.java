package com.example.timecapsule.service;

import com.example.timecapsule.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender sender; // null when spring.mail.* is not configured
    private final String from;

    public MailService(ObjectProvider<JavaMailSender> senderProvider,
                       @Value("${app.mail.from:}") String from) {
        this.sender = senderProvider.getIfAvailable();
        this.from = from;
    }

    /** Like send(), but never throws: returns false if the email could not be sent. */
    public boolean trySend(String to, String subject, String body) {
        try {
            send(to, subject, body);
            return true;
        } catch (ApiException e) {
            return false; // already logged inside send()
        }
    }

    public void send(String to, String subject, String body) {
        if (sender == null) {
            // Development mode: no SMTP configured, so the email is printed here instead
            log.info("\n===== [DEV EMAIL - mail is not configured] =====\nTo: {}\nSubject: {}\n\n{}\n=================================================",
                    to, subject, body);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (!from.isBlank()) {
                message.setFrom(from);
            }
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
        } catch (Exception e) {
            log.error("Failed to send email to {}", to, e);
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "We could not send the email right now. Please try again later.");
        }
    }
}