package com.swiftdrop.backend.modules.notifications.consumer;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;
import com.swiftdrop.backend.modules.auth.entity.*;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import com.swiftdrop.backend.modules.dispatch.entity.AssignmentStatus;
import com.swiftdrop.backend.modules.dispatch.repository.DispatchCourierAssignmentRepository;
import com.swiftdrop.backend.modules.merchant.repository.MerchantRepository;
import com.swiftdrop.backend.modules.messaging.event.*;
import com.swiftdrop.backend.modules.notifications.config.NotificationRabbitMqConfig;
import com.swiftdrop.backend.modules.notifications.event.*;
import com.swiftdrop.backend.modules.notifications.repository.NotificationRepository;
import com.swiftdrop.backend.modules.notifications.service.*;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;

/** Important operational alerts; customer status notifications remain in their existing consumer. */
@Component
@RequiredArgsConstructor
public class OperationalNotificationConsumer {
    private final UserRepository users;
    private final ParcelRepository parcels;
    private final MerchantRepository merchants;
    private final DispatchCourierAssignmentRepository assignments;
    private final NotificationRepository notifications;
    private final NotificationService inbox;
    private final NotificationDeliveryService delivery;
    private final JsonMapper json;
    private static final Set<AssignmentStatus> ACTIVE = Set.of(AssignmentStatus.ASSIGNED, AssignmentStatus.ACCEPTED);

    @RabbitListener(queues = NotificationRabbitMqConfig.OPERATIONS_QUEUE,
        containerFactory = com.swiftdrop.backend.modules.messaging.config.MessagingTopology.WEBHOOK_LISTENER_FACTORY)
    public void onInboxEvent(DomainEventMessage event) {
        for (Notice notice : notices(event)) {
            UUID key = UUID.nameUUIDFromBytes((event.eventId() + ":" + notice.userId() + ":" + notice.type()).getBytes(StandardCharsets.UTF_8));
            if (notifications.existsByEventId(key)) continue;
            try {
                inbox.createEventNotification(key, notice.userId(), notice.parcelId(), notice.title(), notice.message(), notice.type());
            } catch (DataIntegrityViolationException ex) {
                if (!notifications.existsByEventId(key)) throw ex;
            }
        }
    }

    @RabbitListener(queues = NotificationRabbitMqConfig.IMPORTANT_EMAIL_QUEUE,
        containerFactory = com.swiftdrop.backend.modules.messaging.config.MessagingTopology.WEBHOOK_LISTENER_FACTORY)
    public void onEmailEvent(DomainEventMessage event) {
        if (Set.of("ACCOUNT_SUSPENDED", "ACCOUNT_RESTORED").contains(event.eventType())) {
            var payload = json.treeToValue(event.payload(), AccountAccessPayload.class);
            var user = users.findById(payload.userId()).orElse(null);
            if (user == null || user.getRole() == Role.ROLE_MERCHANT || !user.isEmailVerified()) return;
            boolean suspended = "ACCOUNT_SUSPENDED".equals(event.eventType());
            delivery.deliverRequiredEmail(user.getId(), suspended ? "Account suspended" : "Account access restored",
                suspended ? "An administrator has suspended your SwiftDrop account. Contact support if you need help."
                          : "An administrator has restored your SwiftDrop account access. You can sign in again.");
            return;
        }
        for (Notice notice : notices(event)) {
            if (!"MERCHANT_PARCEL_OUTCOME".equals(notice.type())) continue;
            var user = users.findById(notice.userId()).orElse(null);
            if (user != null && user.isEmailVerified()) delivery.deliverRequiredEmail(notice.userId(), notice.title(), notice.message());
        }
    }

    private List<Notice> notices(DomainEventMessage event) {
        List<Notice> result = new ArrayList<>();
        if (Set.of("ASSIGNMENT_CREATED", "ASSIGNMENT_CANCELLED", "ASSIGNMENT_REJECTED").contains(event.eventType())) {
            var p = json.treeToValue(event.payload(), AssignmentNoticePayload.class);
            var assignment = assignments.findById(p.assignmentId()).orElse(null);
            var parcel = parcels.findById(p.parcelId()).orElse(null);
            if (assignment == null || parcel == null || !Objects.equals(assignment.getParcelId(), p.parcelId())
                || !Objects.equals(assignment.getCourierId(), p.courierId()) || !Objects.equals(assignment.getDispatcherId(), p.dispatcherId())) return result;
            boolean rejected = "ASSIGNMENT_REJECTED".equals(event.eventType());
            if ("ASSIGNMENT_CREATED".equals(event.eventType()) && !ACTIVE.contains(assignment.getStatus())) return result;
            UUID recipient = rejected ? p.dispatcherId() : p.courierId();
            if (!eligible(recipient, rejected ? Set.of(Role.ROLE_ADMIN, Role.ROLE_DISPATCHER) : Set.of(Role.ROLE_COURIER))) return result;
            String title = rejected ? "Courier assignment declined" : "ASSIGNMENT_CREATED".equals(event.eventType()) ? "New courier assignment" : "Courier assignment cancelled";
            String message = "Parcel " + parcel.getTrackingNumber() + ": " + (rejected ? "the courier declined this assignment. Review the dispatch queue." : "ASSIGNMENT_CREATED".equals(event.eventType()) ? "a delivery has been assigned to you." : "your assignment has been cancelled.");
            result.add(new Notice(recipient, p.parcelId(), title, message, event.eventType()));
        } else if (ParcelEvents.STATUS_CHANGED.equals(event.eventType())) {
            var p = json.treeToValue(event.payload(), ParcelStatusChangedPayload.class);
            if (p.status() == null || !Set.of("DELIVERED", "FAILED", "CANCELLED").contains(p.status().name())) return result;
            var parcel = parcels.findById(p.parcelId()).orElse(null);
            if (parcel == null) return result;
            String message = "Parcel " + parcel.getTrackingNumber() + " is now " + p.status().name().toLowerCase(Locale.ROOT) + ".";
            if (parcel.getMerchantId() != null) merchants.findById(parcel.getMerchantId()).ifPresent(merchant -> {
                if (eligible(merchant.getUserId(), Set.of(Role.ROLE_MERCHANT))) result.add(new Notice(merchant.getUserId(), parcel.getId(), "Merchant parcel update", message, "MERCHANT_PARCEL_OUTCOME"));
            });
            if ("FAILED".equals(p.status().name())) assignments.findByParcelIdOrderByAssignedAtDesc(parcel.getId()).stream()
                    // Indu's synchronous listener has already completed the assignment by delivery time.
                    .filter(a -> ACTIVE.contains(a.getStatus()) || a.getStatus() == AssignmentStatus.COMPLETED)
                    .filter(a -> p.changedAt() == null || a.getAssignedAt() == null || !a.getAssignedAt().isAfter(p.changedAt()))
                    .findFirst().ifPresent(assignment -> {
                if (eligible(assignment.getDispatcherId(), Set.of(Role.ROLE_ADMIN, Role.ROLE_DISPATCHER))) result.add(new Notice(assignment.getDispatcherId(), parcel.getId(), "Delivery needs attention", message + " Review the courier workload.", "DELIVERY_FAILED"));
            });
        }
        return result;
    }

    private boolean eligible(UUID id, Set<Role> roles) {
        return id != null && users.findById(id).filter(u -> u.getStatus() == AccountStatus.ACTIVE && roles.contains(u.getRole())).isPresent();
    }
    private record Notice(UUID userId, UUID parcelId, String title, String message, String type) {}
}
