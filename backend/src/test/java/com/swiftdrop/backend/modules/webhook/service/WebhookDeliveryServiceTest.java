package com.swiftdrop.backend.modules.webhook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import com.swiftdrop.backend.modules.webhook.endpoint.MerchantWebhookDirectory;
import com.swiftdrop.backend.modules.webhook.entity.WebhookDelivery;
import com.swiftdrop.backend.modules.webhook.entity.WebhookDeliveryStatus;
import com.swiftdrop.backend.modules.webhook.exception.WebhookException;
import com.swiftdrop.backend.modules.webhook.repository.WebhookDeliveryRepository;

/**
 * Merchant isolation: a merchant only ever sees and retries their own deliveries.
 */
@ExtendWith(MockitoExtension.class)
class WebhookDeliveryServiceTest {

    private static final String MERCHANT_EMAIL = "orders@kandy-crafts.lk";
    private static final UUID MERCHANT_ID = UUID.randomUUID();
    private static final UUID OTHER_MERCHANT_ID = UUID.randomUUID();
    private static final Pageable PAGE = PageRequest.of(0, 20);

    @Mock
    private WebhookDeliveryRepository repository;
    @Mock
    private MerchantWebhookDirectory directory;
    @Mock
    private WebhookDispatchService dispatchService;

    @InjectMocks
    private WebhookDeliveryService service;

    @Test
    void merchantListIsScopedToTheirOwnMerchantId() {
        when(directory.findMerchantIdForUser(MERCHANT_EMAIL)).thenReturn(Optional.of(MERCHANT_ID));
        when(repository.findByMerchantId(MERCHANT_ID, PAGE)).thenReturn(new PageImpl<>(List.of(delivery(MERCHANT_ID))));

        assertThat(service.listForMerchant(MERCHANT_EMAIL, null, PAGE).getContent()).hasSize(1);
        verify(repository, never()).findAll(any(Pageable.class));
    }

    @Test
    void merchantListCanFilterByStatus() {
        when(directory.findMerchantIdForUser(MERCHANT_EMAIL)).thenReturn(Optional.of(MERCHANT_ID));
        when(repository.findByMerchantIdAndStatus(MERCHANT_ID, WebhookDeliveryStatus.DEAD, PAGE))
                .thenReturn(new PageImpl<>(List.of()));

        assertThat(service.listForMerchant(MERCHANT_EMAIL, WebhookDeliveryStatus.DEAD, PAGE)).isEmpty();
    }

    @Test
    void merchantCannotSeeAnotherMerchantsDelivery() {
        WebhookDelivery someoneElses = delivery(OTHER_MERCHANT_ID);
        when(directory.findMerchantIdForUser(MERCHANT_EMAIL)).thenReturn(Optional.of(MERCHANT_ID));
        when(repository.findById(someoneElses.getId())).thenReturn(Optional.of(someoneElses));

        assertThatThrownBy(() -> service.getForMerchant(MERCHANT_EMAIL, someoneElses.getId()))
                .isInstanceOf(WebhookException.class)
                .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void merchantCannotRetryAnotherMerchantsDelivery() {
        WebhookDelivery someoneElses = delivery(OTHER_MERCHANT_ID);
        when(directory.findMerchantIdForUser(MERCHANT_EMAIL)).thenReturn(Optional.of(MERCHANT_ID));
        when(repository.findById(someoneElses.getId())).thenReturn(Optional.of(someoneElses));

        assertThatThrownBy(() -> service.retryForMerchant(MERCHANT_EMAIL, someoneElses.getId()))
                .isInstanceOf(WebhookException.class);
        verify(dispatchService, never()).retryNow(any());
    }

    @Test
    void userWithoutMerchantAccountIsRefused() {
        when(directory.findMerchantIdForUser("customer@gmail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listForMerchant("customer@gmail.com", null, PAGE))
                .isInstanceOf(WebhookException.class)
                .extracting("status").isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void onlyDeadDeliveriesCanBeRetriedManually() {
        WebhookDelivery pending = delivery(MERCHANT_ID);
        when(repository.findById(pending.getId())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.retryAsAdmin(pending.getId()))
                .isInstanceOf(WebhookException.class)
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void adminRetryOfDeadDeliveryRunsAnAttempt() {
        WebhookDelivery dead = delivery(MERCHANT_ID);
        dead.markDead();
        when(repository.findById(dead.getId())).thenReturn(Optional.of(dead));
        when(dispatchService.retryNow(dead)).thenReturn(dead);

        assertThat(service.retryAsAdmin(dead.getId())).isSameAs(dead);
    }

    @Test
    void adminListFiltersByMerchantAndStatus() {
        when(repository.findByMerchantIdAndStatus(MERCHANT_ID, WebhookDeliveryStatus.DEAD, PAGE))
                .thenReturn(new PageImpl<>(List.of()));
        when(repository.findByStatus(WebhookDeliveryStatus.RETRYING, PAGE)).thenReturn(new PageImpl<>(List.of()));
        when(repository.findAll(PAGE)).thenReturn(new PageImpl<>(List.of()));

        service.listAll(MERCHANT_ID, WebhookDeliveryStatus.DEAD, PAGE);
        service.listAll(null, WebhookDeliveryStatus.RETRYING, PAGE);
        service.listAll(null, null, PAGE);

        verify(repository).findByMerchantIdAndStatus(MERCHANT_ID, WebhookDeliveryStatus.DEAD, PAGE);
        verify(repository).findByStatus(WebhookDeliveryStatus.RETRYING, PAGE);
        verify(repository).findAll(PAGE);
    }

    @Test
    void unknownDeliveryIsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
                .isInstanceOf(WebhookException.class)
                .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static WebhookDelivery delivery(UUID merchantId) {
        return WebhookDelivery.create(UUID.randomUUID(), merchantId, UUID.randomUUID(), "SDP7845621",
                "parcel.status_changed", "https://shop.example.lk/hook", "{}", 6);
    }
}
