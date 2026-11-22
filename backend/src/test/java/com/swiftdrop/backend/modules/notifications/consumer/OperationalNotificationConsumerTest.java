package com.swiftdrop.backend.modules.notifications.consumer;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import com.swiftdrop.backend.modules.auth.entity.*;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import com.swiftdrop.backend.modules.dispatch.entity.*;
import com.swiftdrop.backend.modules.dispatch.repository.DispatchCourierAssignmentRepository;
import com.swiftdrop.backend.modules.merchant.entity.Merchant;
import com.swiftdrop.backend.modules.merchant.repository.MerchantRepository;
import com.swiftdrop.backend.modules.messaging.event.*;
import com.swiftdrop.backend.modules.notifications.event.*;
import com.swiftdrop.backend.modules.notifications.repository.NotificationRepository;
import com.swiftdrop.backend.modules.notifications.service.*;
import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import tools.jackson.databind.json.JsonMapper;

class OperationalNotificationConsumerTest {
    final UserRepository users = mock(UserRepository.class);
    final ParcelRepository parcels = mock(ParcelRepository.class);
    final MerchantRepository merchants = mock(MerchantRepository.class);
    final DispatchCourierAssignmentRepository assignments = mock(DispatchCourierAssignmentRepository.class);
    final NotificationRepository notifications = mock(NotificationRepository.class);
    final NotificationService inbox = mock(NotificationService.class);
    final NotificationDeliveryService delivery = mock(NotificationDeliveryService.class);
    final JsonMapper json = JsonMapper.builder().build();
    final OperationalNotificationConsumer consumer = new OperationalNotificationConsumer(users, parcels, merchants, assignments, notifications, inbox, delivery, json);
    final UUID parcelId = UUID.randomUUID(), courierId = UUID.randomUUID(), dispatcherId = UUID.randomUUID(), assignmentId = UUID.randomUUID(), merchantId = UUID.randomUUID(), merchantUserId = UUID.randomUUID();

    DomainEventMessage event(String type, Object payload) {
        return new DomainEventMessage(UUID.randomUUID(), type, "TEST", parcelId, Instant.now(), json.valueToTree(payload));
    }
    DomainEventMessage assignment(String type, AssignmentStatus status) {
        when(parcels.findById(parcelId)).thenReturn(Optional.of(Parcel.builder().id(parcelId).trackingNumber("SDP123").build()));
        when(assignments.findById(assignmentId)).thenReturn(Optional.of(CourierAssignment.builder().id(assignmentId).parcelId(parcelId).courierId(courierId).dispatcherId(dispatcherId).status(status).build()));
        return event(type, new AssignmentNoticePayload(assignmentId, parcelId, courierId, dispatcherId, null));
    }
    void active(UUID id, Role role) {
        when(users.findById(id)).thenReturn(Optional.of(User.builder().id(id).role(role).status(AccountStatus.ACTIVE).emailVerified(true).build()));
    }
    @Test void assignmentGoesOnlyToCourierAndReplayIsIgnored() {
        var event = assignment("ASSIGNMENT_CREATED", AssignmentStatus.ASSIGNED);
        active(courierId, Role.ROLE_COURIER);
        Set<UUID> saved = new HashSet<>();
        when(notifications.existsByEventId(any())).thenAnswer(i -> saved.contains(i.getArgument(0)));
        when(inbox.createEventNotification(any(), any(), any(), any(), any(), any())).thenAnswer(i -> { saved.add(i.getArgument(0)); return null; });
        consumer.onInboxEvent(event); consumer.onInboxEvent(event);
        verify(inbox).createEventNotification(any(), eq(courierId), eq(parcelId), anyString(), contains("SDP123"), eq("ASSIGNMENT_CREATED"));
        verifyNoInteractions(delivery);
    }
    @Test void obsoleteCreatedAssignmentIsSkipped() {
        consumer.onInboxEvent(assignment("ASSIGNMENT_CREATED", AssignmentStatus.CANCELLED));
        verifyNoInteractions(inbox);
    }
    @Test void rejectionGoesToResponsibleDispatcher() {
        var event = assignment("ASSIGNMENT_REJECTED", AssignmentStatus.REJECTED);
        active(dispatcherId, Role.ROLE_DISPATCHER);
        consumer.onInboxEvent(event);
        verify(inbox).createEventNotification(any(), eq(dispatcherId), eq(parcelId), anyString(), anyString(), eq("ASSIGNMENT_REJECTED"));
    }
    @Test void cancelledAssignmentNotifiesOldCourier() {
        var event = assignment("ASSIGNMENT_CANCELLED", AssignmentStatus.CANCELLED);
        active(courierId, Role.ROLE_COURIER);
        consumer.onInboxEvent(event);
        verify(inbox).createEventNotification(any(), eq(courierId), eq(parcelId), anyString(), anyString(), eq("ASSIGNMENT_CANCELLED"));
    }
    @Test void wrongRoleRecipientIsSkipped() {
        var event = assignment("ASSIGNMENT_CREATED", AssignmentStatus.ASSIGNED);
        active(courierId, Role.ROLE_CUSTOMER);
        consumer.onInboxEvent(event);
        verifyNoInteractions(inbox);
    }
    DomainEventMessage outcome(ParcelStatus status) {
        when(parcels.findById(parcelId)).thenReturn(Optional.of(Parcel.builder().id(parcelId).merchantId(merchantId).trackingNumber("SDP123").build()));
        when(merchants.findById(merchantId)).thenReturn(Optional.of(Merchant.builder().id(merchantId).userId(merchantUserId).build()));
        active(merchantUserId, Role.ROLE_MERCHANT);
        return event(ParcelEvents.STATUS_CHANGED, new ParcelStatusChangedPayload(parcelId, "SDP123", null, status, null, null));
    }
    @Test void failureNotifiesMerchantAndResponsibleOperator() {
        var event = outcome(ParcelStatus.FAILED);
        when(assignments.findByParcelIdOrderByAssignedAtDesc(parcelId)).thenReturn(List.of(CourierAssignment.builder().dispatcherId(dispatcherId).status(AssignmentStatus.COMPLETED).build()));
        active(dispatcherId, Role.ROLE_ADMIN);
        consumer.onInboxEvent(event);
        verify(inbox).createEventNotification(any(), eq(merchantUserId), eq(parcelId), anyString(), anyString(), eq("MERCHANT_PARCEL_OUTCOME"));
        verify(inbox).createEventNotification(any(), eq(dispatcherId), eq(parcelId), anyString(), anyString(), eq("DELIVERY_FAILED"));
    }
    @Test void merchantEmailFailurePropagatesForRetryWithoutBlockingInbox() {
        var event = outcome(ParcelStatus.DELIVERED);
        doThrow(new MailSendException("SMTP unavailable")).when(delivery).deliverRequiredEmail(any(), any(), any());
        consumer.onInboxEvent(event);
        assertThrows(MailSendException.class, () -> consumer.onEmailEvent(event));
        verify(inbox).createEventNotification(any(), eq(merchantUserId), any(), any(), any(), any());
    }
    @Test void routineParcelStatusDoesNotGenerateMoreNotifications() {
        consumer.onInboxEvent(event(ParcelEvents.STATUS_CHANGED, new ParcelStatusChangedPayload(parcelId, "SDP123", null, ParcelStatus.CREATED, null, null)));
        verifyNoInteractions(inbox, delivery, parcels);
    }
    @Test void suspendedAccountGetsEmailOnly() {
        active(dispatcherId, Role.ROLE_DISPATCHER);
        consumer.onEmailEvent(event("ACCOUNT_SUSPENDED", new AccountAccessPayload(dispatcherId)));
        verify(delivery).deliverRequiredEmail(eq(dispatcherId), eq("Account suspended"), anyString());
        verifyNoInteractions(inbox);
    }
    @Test void merchantDoesNotReceiveGenericAccountAccessEmail() {
        active(merchantUserId, Role.ROLE_MERCHANT);
        consumer.onEmailEvent(event("ACCOUNT_RESTORED", new AccountAccessPayload(merchantUserId)));
        verifyNoInteractions(delivery);
    }
}
