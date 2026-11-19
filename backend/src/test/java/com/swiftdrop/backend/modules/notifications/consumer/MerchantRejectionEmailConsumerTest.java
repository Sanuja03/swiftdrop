package com.swiftdrop.backend.modules.notifications.consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.swiftdrop.backend.modules.merchant.event.MerchantRejectedPayload;
import com.swiftdrop.backend.modules.merchant.event.MerchantApprovedPayload;
import com.swiftdrop.backend.modules.messaging.event.DomainEventMessage;
import com.swiftdrop.backend.modules.notifications.repository.NotificationRepository;
import com.swiftdrop.backend.modules.notifications.service.NotificationService;
import com.swiftdrop.backend.modules.notifications.service.NotificationDeliveryService;
import tools.jackson.databind.json.JsonMapper;

class MerchantRejectionEmailConsumerTest {
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final NotificationService service = mock(NotificationService.class);
    private final NotificationDeliveryService delivery = mock(NotificationDeliveryService.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final MerchantRejectionEmailConsumer emails = new MerchantRejectionEmailConsumer(delivery, mapper);
    private final MerchantDecisionNotificationConsumer inApp = new MerchantDecisionNotificationConsumer(notifications, service, mapper);
    private final UUID eventId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private DomainEventMessage event(String type, String reason) {
        UUID merchantId = UUID.randomUUID();
        Object payload = "MERCHANT_APPROVED".equals(type) ? new MerchantApprovedPayload(merchantId, userId, "Test Shop")
                : new MerchantRejectedPayload(merchantId, userId, "Test Shop", reason);
        return new DomainEventMessage(eventId, type, "MERCHANT", merchantId, Instant.now(), mapper.valueToTree(payload));
    }

    @Test
    void rejectionProducesBothChannelsWithTheExactReason() {
        var event = event("MERCHANT_REJECTED", "Business contact details are incomplete.");
        emails.onDecision(event);
        inApp.onDecision(event);
        verify(delivery).deliverRequiredEmail(eq(userId), eq("Merchant Application Rejected"),
                contains("Reason:\nBusiness contact details are incomplete."));
        verify(service).createEventNotification(eq(eventId), eq(userId), isNull(),
                eq("Merchant Application Rejected"), contains("Business contact details are incomplete."), eq("MERCHANT_REJECTED"));
    }

    @Test
    void approvalProducesBothChannelsWithoutExposingKeys() {
        var event = event("MERCHANT_APPROVED", null);
        emails.onDecision(event);
        inApp.onDecision(event);
        verify(delivery).deliverRequiredEmail(eq(userId), eq("Merchant Application Approved"), contains("has been approved"));
        verify(service).createEventNotification(eq(eventId), eq(userId), isNull(),
                eq("Merchant Application Approved"), contains("verifying your email"), eq("MERCHANT_APPROVED"));
    }

    @Test
    void completedInAppRedeliveryDoesNotDuplicateTheNotificationOrSuppressEmail() {
        when(notifications.existsByEventId(eventId)).thenReturn(true);
        var event = event("MERCHANT_APPROVED", null);
        inApp.onDecision(event);
        emails.onDecision(event);
        verifyNoInteractions(service);
        verify(delivery).deliverRequiredEmail(any(), any(), any());
    }

    @Test
    void emailFailureDoesNotPreventTheIndependentInAppChannel() {
        doThrow(new IllegalStateException("SMTP unavailable")).when(delivery).deliverRequiredEmail(any(), any(), any());
        var event = event("MERCHANT_REJECTED", "Reason");
        assertThrows(IllegalStateException.class, () -> emails.onDecision(event));
        inApp.onDecision(event);
        verify(service).createEventNotification(eq(eventId), eq(userId), isNull(), any(), any(), eq("MERCHANT_REJECTED"));
    }

    @Test
    void malformedOrUnrelatedEventsDoNotSendAnEmail() {
        emails.onDecision(event("MERCHANT_REGISTERED", "Reason"));
        assertThrows(IllegalArgumentException.class, () -> emails.onDecision(event("MERCHANT_REJECTED", " ")));
        verifyNoInteractions(delivery);
    }
}
