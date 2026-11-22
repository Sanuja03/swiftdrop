package com.swiftdrop.backend.modules.notifications.service;

import com.swiftdrop.backend.modules.auth.entity.User;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class NotificationDeliveryService {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationDeliveryService.class);

    private final UserRepository userRepository;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String senderEmail;

    public NotificationDeliveryService(
            UserRepository userRepository,
            JavaMailSender mailSender
    ) {
        this.userRepository = userRepository;
        this.mailSender = mailSender;
    }

    public void deliver(UUID customerId, String title, String message) {
        User user = userRepository.findById(customerId).orElse(null);

        if (user == null) {
            log.warn("Notification delivery skipped: customer {} not found", customerId);
            return;
        }

        sendEmail(user.getEmail(), title, message);
        sendMockSms(user.getPhone(), message);
    }

    private void sendEmail(String email, String title, String message) {
        if (email == null || email.isBlank()) {
            log.warn("Notification email skipped: no email address");
            return;
        }

        if (senderEmail == null || senderEmail.isBlank()) {
            log.warn("Notification email skipped: SMTP sender not configured. Set SPRING_MAIL_USERNAME and SPRING_MAIL_PASSWORD in the backend environment.");
            return;
        }

        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(senderEmail);
            mail.setTo(email);
            mail.setSubject("SwiftDrop - " + title);
            mail.setText(message);

            mailSender.send(mail);

            log.info("Parcel notification email accepted by SMTP server; inbox delivery is not confirmed");
        } catch (MailAuthenticationException ex) {
            log.error("Parcel notification email failed: SMTP authentication rejected. Check SPRING_MAIL_USERNAME and SPRING_MAIL_PASSWORD; Gmail requires an app password.");
        } catch (Exception ex) {
            log.error("Parcel notification email failed ({}). Check SMTP connectivity and sender configuration.", ex.getClass().getSimpleName());
        }
    }

    /** Required transactional-event email: failures propagate so the broker retains failed work. */
    public void deliverRequiredEmail(UUID userId, String title, String message) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Email recipient account not found"));
        if (senderEmail == null || senderEmail.isBlank() || user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalStateException("Notification email sender or recipient is not configured");
        }
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(senderEmail);
        mail.setTo(user.getEmail());
        mail.setSubject("SwiftDrop - " + title);
        mail.setText(message);
        mailSender.send(mail);
        log.info("Notification email accepted by SMTP server; inbox delivery is not confirmed");
    }

    private void sendMockSms(String phone, String message) {
        if (phone == null || phone.isBlank()) {
            log.info("Mock SMS skipped: customer has no phone number");
            return;
        }

        // Mock only: no external SMS provider is contacted.
        log.info("[MOCK SMS] Parcel notification prepared for customer phone ending in {}",
                phone.length() >= 4 ? phone.substring(phone.length() - 4) : "****");
    }
}
