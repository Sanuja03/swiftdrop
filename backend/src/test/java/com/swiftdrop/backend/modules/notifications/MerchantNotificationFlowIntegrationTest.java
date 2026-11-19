package com.swiftdrop.backend.modules.notifications;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.backend.modules.auth.entity.*;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import com.swiftdrop.backend.modules.merchant.dto.MerchantRegisterRequest;
import com.swiftdrop.backend.modules.merchant.service.MerchantService;
import com.swiftdrop.backend.modules.messaging.event.DomainEventMessage;
import com.swiftdrop.backend.modules.messaging.repository.OutboxEventRepository;
import com.swiftdrop.backend.modules.notifications.consumer.MerchantNotificationConsumer;
import com.swiftdrop.backend.modules.notifications.service.NotificationService;
import tools.jackson.databind.json.JsonMapper;

/** Exercises real H2 repositories and services without sending mail or using the shared broker. */
@SpringBootTest(properties = {"app.messaging.outbox.publisher-enabled=false", "management.health.mail.enabled=false"})
@Transactional
class MerchantNotificationFlowIntegrationTest {
    @Autowired private UserRepository users;
    @Autowired private MerchantService merchants;
    @Autowired private OutboxEventRepository outbox;
    @Autowired private MerchantNotificationConsumer consumer;
    @Autowired private NotificationService notifications;
    @Autowired private JsonMapper mapper;
    @MockitoBean private JavaMailSender mail;

    private User user(Role role, AccountStatus status) {
        return users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.test")
                .fullName("Isolated Merchant Alert Test").role(role).status(status).build());
    }

    @Test
    void registrationOutboxAlertsAdministratorsButNotDispatchers() {
        var admin = user(Role.ROLE_ADMIN, AccountStatus.ACTIVE);
        var dispatcher = user(Role.ROLE_DISPATCHER, AccountStatus.ACTIVE);
        var suspended = user(Role.ROLE_ADMIN, AccountStatus.SUSPENDED);
        var merchantUser = user(Role.ROLE_MERCHANT, AccountStatus.ACTIVE);
        var profile = merchants.registerMerchant(merchantUser.getId(), MerchantRegisterRequest.builder()
                .businessName("Isolated Test Shop").registrationNumber("TEST-REG").contactPhone("0000000000").businessAddress("Test address").build());
        var row = outbox.findAll().stream().filter(e -> e.getAggregateId().equals(profile.getId())).findFirst().orElseThrow();
        assertEquals("merchant.registered", row.getRoutingKey());
        assertFalse(row.getPayload().contains("whsec_"));
        var event = new DomainEventMessage(row.getId(), row.getEventType(), row.getAggregateType(),
                row.getAggregateId(), row.getCreatedAt(), mapper.readTree(row.getPayload()));
        consumer.onMerchantEvent(event);
        consumer.onMerchantEvent(event);
        for (var recipient : new User[]{admin}) {
            var received = notifications.getMyNotifications(recipient.getId());
            assertEquals(1, received.size());
            assertEquals("MERCHANT_APPROVAL", received.getFirst().notificationType());
            assertNull(received.getFirst().parcelId());
            assertEquals(1, notifications.getUnreadCount(recipient.getId()));
            notifications.markAsRead(received.getFirst().id(), recipient.getId());
            assertEquals(0, notifications.getUnreadCount(recipient.getId()));
        }
        assertTrue(notifications.getMyNotifications(suspended.getId()).isEmpty());
        assertTrue(notifications.getMyNotifications(dispatcher.getId()).isEmpty());
        assertTrue(notifications.getMyNotifications(merchantUser.getId()).isEmpty());
        verifyNoInteractions(mail);
    }
}
