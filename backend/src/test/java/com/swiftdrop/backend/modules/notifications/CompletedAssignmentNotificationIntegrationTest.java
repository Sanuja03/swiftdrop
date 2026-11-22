package com.swiftdrop.backend.modules.notifications;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import com.swiftdrop.backend.modules.auth.entity.*;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import com.swiftdrop.backend.modules.dispatch.entity.*;
import com.swiftdrop.backend.modules.dispatch.repository.DispatchCourierAssignmentRepository;
import com.swiftdrop.backend.modules.messaging.event.DomainEventMessage;
import com.swiftdrop.backend.modules.messaging.repository.OutboxEventRepository;
import com.swiftdrop.backend.modules.notifications.consumer.OperationalNotificationConsumer;
import com.swiftdrop.backend.modules.notifications.service.NotificationService;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.modules.tracking.service.ParcelStatusService;
import com.swiftdrop.backend.support.TestParcels;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {"app.messaging.outbox.publisher-enabled=false", "management.health.mail.enabled=false"})
@Transactional
class CompletedAssignmentNotificationIntegrationTest {
    @Autowired UserRepository users;
    @Autowired ParcelRepository parcels;
    @Autowired ParcelStatusService statuses;
    @Autowired DispatchCourierAssignmentRepository assignments;
    @Autowired OutboxEventRepository outbox;
    @Autowired OperationalNotificationConsumer consumer;
    @Autowired NotificationService inbox;
    @Autowired JsonMapper json;
    @MockitoBean JavaMailSender mail;

    private User user(Role role) {
        return users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.test")
                .fullName("Synthetic Notification Test User")
                .role(role).status(AccountStatus.ACTIVE).build());
    }

    @Test
    void failureAlertsResponsibleDispatcherAfterIndusListenerCompletesAssignment() {
        var dispatcher = user(Role.ROLE_DISPATCHER);
        var courier = user(Role.ROLE_COURIER);
        var customer = user(Role.ROLE_CUSTOMER);
        var parcel = TestParcels.ownedBy(UUID.randomUUID(), customer.getId());
        ReflectionTestUtils.setField(parcel, "id", null);
        ReflectionTestUtils.setField(parcel, "trackingNumber", "TEST" + UUID.randomUUID().toString().substring(0, 12));
        parcel = parcels.saveAndFlush(parcel);
        statuses.updateStatus(parcel.getId(), ParcelStatus.PICKED_UP, null, courier.getId());
        statuses.updateStatus(parcel.getId(), ParcelStatus.IN_TRANSIT, null, courier.getId());
        statuses.updateStatus(parcel.getId(), ParcelStatus.OUT_FOR_DELIVERY, null, courier.getId());
        var assignment = assignments.saveAndFlush(CourierAssignment.builder().parcelId(parcel.getId())
                .courierId(courier.getId()).dispatcherId(dispatcher.getId()).status(AssignmentStatus.ACCEPTED).build());
        statuses.updateStatus(parcel.getId(), ParcelStatus.FAILED, "Synthetic delivery failure", courier.getId());
        assignments.flush();
        assertEquals(AssignmentStatus.COMPLETED, assignments.findById(assignment.getId()).orElseThrow().getStatus());
        UUID parcelId = parcel.getId();
        var row = outbox.findAll().stream().filter(e -> e.getAggregateId().equals(parcelId)
                && "parcel.status.failed".equals(e.getRoutingKey())).findFirst().orElseThrow();
        var event = new DomainEventMessage(row.getId(), row.getEventType(), row.getAggregateType(),
                row.getAggregateId(), row.getCreatedAt(), json.readTree(row.getPayload()));
        consumer.onInboxEvent(event);
        consumer.onInboxEvent(event);
        var received = inbox.getMyNotifications(dispatcher.getId());
        assertEquals(1, received.size());
        assertEquals("DELIVERY_FAILED", received.getFirst().notificationType());
        assertTrue(inbox.getMyNotifications(courier.getId()).isEmpty());
        assertTrue(inbox.getMyNotifications(customer.getId()).isEmpty());
        verifyNoInteractions(mail);
    }
}
