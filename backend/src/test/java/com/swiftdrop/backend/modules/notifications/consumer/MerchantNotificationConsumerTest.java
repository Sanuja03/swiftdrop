package com.swiftdrop.backend.modules.notifications.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import com.swiftdrop.backend.modules.auth.entity.*;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import com.swiftdrop.backend.modules.merchant.entity.*;
import com.swiftdrop.backend.modules.merchant.event.MerchantRegisteredPayload;
import com.swiftdrop.backend.modules.merchant.repository.MerchantRepository;
import com.swiftdrop.backend.modules.messaging.event.DomainEventMessage;
import com.swiftdrop.backend.modules.notifications.repository.NotificationRepository;
import com.swiftdrop.backend.modules.notifications.service.NotificationService;
import tools.jackson.databind.json.JsonMapper;

class MerchantNotificationConsumerTest {
    private final UserRepository users = mock(UserRepository.class);
    private final MerchantRepository merchants = mock(MerchantRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final NotificationService service = mock(NotificationService.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final UUID merchantId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final User admin = User.builder().id(UUID.randomUUID()).role(Role.ROLE_ADMIN).build();
    private final User dispatcher = User.builder().id(UUID.randomUUID()).role(Role.ROLE_DISPATCHER).build();
    private final Merchant merchant = Merchant.builder().id(merchantId).businessName("Test Shop")
            .registrationStatus(MerchantRegistrationStatus.PENDING).build();
    private final MerchantNotificationConsumer consumer = new MerchantNotificationConsumer(
            users, merchants, notifications, service, mapper);

    @BeforeEach
    void setup() {
        when(merchants.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(users.findByRole(Role.ROLE_ADMIN)).thenReturn(List.of(admin));
    }

    private DomainEventMessage event(String type) {
        return new DomainEventMessage(eventId, type, "MERCHANT", merchantId, Instant.now(),
                mapper.valueToTree(new MerchantRegisteredPayload(merchantId)));
    }

    @Test
    void alertsOnlyActiveAdministrators() {
        consumer.onMerchantEvent(event(MerchantRegisteredPayload.EVENT_TYPE));
        var ids = ArgumentCaptor.forClass(UUID.class);
        var recipients = ArgumentCaptor.forClass(UUID.class);
        verify(service).createEventNotification(ids.capture(), recipients.capture(), isNull(),
                eq("Merchant Approval Pending"), contains("Test Shop"), eq("MERCHANT_APPROVAL"));
        assertThat(ids.getAllValues()).doesNotHaveDuplicates();
        assertThat(recipients.getAllValues()).containsExactly(admin.getId());
        verify(users, never()).findByRole(Role.ROLE_DISPATCHER);
        verify(users, never()).findByRole(Role.ROLE_CUSTOMER);
        verify(users, never()).findByRole(Role.ROLE_COURIER);
        verify(users, never()).findByRole(Role.ROLE_MERCHANT);
    }

    @Test
    void retryKeepsIdsStableAndDoesNotRepeatPersistedRecipients() {
        Set<UUID> saved = new HashSet<>();
        when(notifications.existsByEventId(any())).thenAnswer(inv -> saved.contains(inv.getArgument(0)));
        when(service.createEventNotification(any(), any(), isNull(), any(), any(), any()))
                .thenAnswer(inv -> { saved.add(inv.getArgument(0)); return null; });
        consumer.onMerchantEvent(event(MerchantRegisteredPayload.EVENT_TYPE));
        consumer.onMerchantEvent(event(MerchantRegisteredPayload.EVENT_TYPE));
        verify(service).createEventNotification(any(), any(), isNull(), any(), any(), any());
    }

    @Test
    void suspendedOperationsAccountsReceiveNothing() {
        admin.setStatus(AccountStatus.SUSPENDED);
        dispatcher.setStatus(AccountStatus.SUSPENDED);
        consumer.onMerchantEvent(event(MerchantRegisteredPayload.EVENT_TYPE));
        verifyNoInteractions(service);
    }

    @Test
    void ignoresAlreadyReviewedMissingAndUnrelatedRegistrations() {
        merchant.setRegistrationStatus(MerchantRegistrationStatus.APPROVED);
        consumer.onMerchantEvent(event(MerchantRegisteredPayload.EVENT_TYPE));
        when(merchants.findById(merchantId)).thenReturn(Optional.empty());
        consumer.onMerchantEvent(event(MerchantRegisteredPayload.EVENT_TYPE));
        consumer.onMerchantEvent(event("PARCEL_STATUS_CHANGED"));
        verifyNoInteractions(service);
    }

    @Test
    void concurrentDuplicateIsIgnoredOnlyIfItsNotificationExists() {
        when(notifications.existsByEventId(any())).thenReturn(false, true, true);
        when(service.createEventNotification(any(), any(), isNull(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        consumer.onMerchantEvent(event(MerchantRegisteredPayload.EVENT_TYPE));
        verify(service).createEventNotification(any(), eq(admin.getId()), isNull(), any(), any(), any());
    }

    @Test
    void storageFailureIsPropagatedSoTheBrokerCanRetry() {
        when(service.createEventNotification(any(), any(), isNull(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("storage failure"));
        assertThrows(DataIntegrityViolationException.class,
                () -> consumer.onMerchantEvent(event(MerchantRegisteredPayload.EVENT_TYPE)));
    }
}
