package com.swiftdrop.backend.modules.notifications;

import com.swiftdrop.backend.modules.auth.entity.*;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import com.swiftdrop.backend.modules.messaging.event.*;
import com.swiftdrop.backend.modules.notifications.consumer.NotificationConsumer;
import com.swiftdrop.backend.modules.notifications.repository.NotificationRepository;
import com.swiftdrop.backend.modules.notifications.service.NotificationService;
import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.support.TestParcels;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"spring.mail.username=notifications@example.test", "management.health.mail.enabled=false"})
@ExtendWith(OutputCaptureExtension.class)
class NotificationFlowIntegrationTest {
    @Autowired private UserRepository users;
    @Autowired private ParcelRepository parcels;
    @Autowired private NotificationRepository notifications;
    @Autowired private NotificationConsumer consumer;
    @Autowired private NotificationService service;
    @Autowired private JsonMapper mapper;
    @MockitoBean private JavaMailSender mail;
    private User customer;
    private Parcel parcel;

    @AfterEach
    void cleanUpOwnTestRows() {
        if (customer != null) notifications.deleteAll(notifications.findByUserIdOrderByCreatedAtDesc(customer.getId()));
        if (parcel != null) parcels.deleteById(parcel.getId());
        if (customer != null) users.deleteById(customer.getId());
    }

    @Test
    void statusEventPersistsOnceSendsMockedEmailAndSupportsUnreadFlow(CapturedOutput output) {
        customer = users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.test")
                .fullName("Notification Test Customer").phone("0701234567")
                .role(Role.ROLE_CUSTOMER).status(AccountStatus.ACTIVE).provider(AuthProvider.LOCAL)
                .emailVerified(true).build());
        parcel = TestParcels.ownedBy(UUID.randomUUID(), customer.getId());
        ReflectionTestUtils.setField(parcel, "id", null);
        ReflectionTestUtils.setField(parcel, "trackingNumber", "TEST" + UUID.randomUUID().toString().substring(0, 12));
        parcel = parcels.saveAndFlush(parcel);
        UUID eventId = UUID.randomUUID();
        var payload = new ParcelStatusChangedPayload(parcel.getId(), parcel.getTrackingNumber(),
                ParcelStatus.CREATED, ParcelStatus.PICKED_UP, "Local isolated integration test", Instant.now());
        var event = new DomainEventMessage(eventId, ParcelEvents.STATUS_CHANGED, "PARCEL", parcel.getId(),
                Instant.now(), mapper.valueToTree(payload));
        consumer.onParcelEvent(event);
        consumer.onParcelEvent(event);
        var saved = service.getMyNotifications(customer.getId());
        assertEquals(1, saved.size());
        assertEquals(1L, service.getUnreadCount(customer.getId()));
        assertEquals(1, service.getUnreadNotifications(customer.getId()).size());
        assertTrue(saved.getFirst().message().contains("picked up"));
        verify(mail, times(1)).send(any(SimpleMailMessage.class));
        assertTrue(output.getAll().contains("[MOCK SMS]"));
        assertTrue(output.getAll().contains("ending in 4567"));
        service.markAsRead(saved.getFirst().id(), customer.getId());
        assertEquals(0L, service.getUnreadCount(customer.getId()));
        assertTrue(service.getUnreadNotifications(customer.getId()).isEmpty());
    }
}
