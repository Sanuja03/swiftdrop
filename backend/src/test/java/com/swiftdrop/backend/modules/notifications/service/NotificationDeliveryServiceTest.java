package com.swiftdrop.backend.modules.notifications.service;

import com.swiftdrop.backend.modules.auth.entity.User;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class NotificationDeliveryServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final JavaMailSender mail = mock(JavaMailSender.class);
    private final NotificationDeliveryService delivery = new NotificationDeliveryService(users, mail);
    private final UUID userId = UUID.randomUUID();

    private void customer() {
        User user = mock(User.class);
        when(user.getEmail()).thenReturn("customer@example.test");
        when(user.getPhone()).thenReturn("0700000000");
        when(users.findById(userId)).thenReturn(Optional.of(user));
    }

    @Test
    void requiredDecisionEmailIncludesTheReasonAndDoesNotSendSms(CapturedOutput output) {
        customer();
        ReflectionTestUtils.setField(delivery, "senderEmail", "notifications@example.test");
        delivery.deliverRequiredEmail(userId, "Merchant Application Rejected", "Reason: Business information is incomplete.");
        var message = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(message.capture());
        assertTrue(message.getValue().getText().contains("Business information is incomplete."));
        assertFalse(output.getAll().contains("[MOCK SMS]"));
    }

    @Test
    void requiredEmailFailurePropagatesInsteadOfBeingMarkedDelivered() {
        customer();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> delivery.deliverRequiredEmail(userId, "Decision", "Reason"));
        ReflectionTestUtils.setField(delivery, "senderEmail", "notifications@example.test");
        doThrow(new MailSendException("Isolated test failure")).when(mail).send(any(SimpleMailMessage.class));
        org.junit.jupiter.api.Assertions.assertThrows(MailSendException.class,
                () -> delivery.deliverRequiredEmail(userId, "Decision", "Reason"));
    }

    @Test
    void sendsConfiguredEmail() {
        customer();
        ReflectionTestUtils.setField(delivery, "senderEmail", "notifications@example.test");
        delivery.deliver(userId, "Parcel updated", "Parcel picked up.");
        SimpleMailMessage expected = new SimpleMailMessage();
        expected.setFrom("notifications@example.test");
        expected.setTo("customer@example.test");
        expected.setSubject("SwiftDrop - Parcel updated");
        expected.setText("Parcel picked up.");
        verify(mail).send(expected);
    }

    @Test
    void unconfiguredEmailStillAllowsMockSms(CapturedOutput output) {
        customer();
        assertDoesNotThrow(() -> delivery.deliver(userId, "Title", "Message"));
        verifyNoInteractions(mail);
        assertTrue(output.getAll().contains("Set SPRING_MAIL_USERNAME and SPRING_MAIL_PASSWORD"));
        assertTrue(output.getAll().contains("[MOCK SMS]"));
    }

    @Test
    void mailFailureDoesNotDiscardInAppNotification() {
        customer();
        ReflectionTestUtils.setField(delivery, "senderEmail", "notifications@example.test");
        doThrow(new MailSendException("Test failure")).when(mail).send(any(SimpleMailMessage.class));
        assertDoesNotThrow(() -> delivery.deliver(userId, "Title", "Message"));
    }

    @Test
    void missingUserSkipsDelivery() {
        when(users.findById(userId)).thenReturn(Optional.empty());
        delivery.deliver(userId, "Title", "Message");
        verifyNoInteractions(mail);
    }

    @Test
    void authenticationFailureExplainsSetupWithoutLeakingServerDetails(CapturedOutput output) {
        customer();
        ReflectionTestUtils.setField(delivery, "senderEmail", "notifications@example.test");
        doThrow(new MailAuthenticationException("private SMTP response and secret"))
                .when(mail).send(any(SimpleMailMessage.class));
        assertDoesNotThrow(() -> delivery.deliver(userId, "Title", "Message"));
        assertTrue(output.getAll().contains("SMTP authentication rejected"));
        assertTrue(output.getAll().contains("[MOCK SMS]"));
        assertFalse(output.getAll().contains("private SMTP response and secret"));
    }
}
