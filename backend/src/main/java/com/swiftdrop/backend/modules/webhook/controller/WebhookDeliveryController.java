package com.swiftdrop.backend.modules.webhook.controller;

import java.security.Principal;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.backend.modules.webhook.dto.PageResponse;
import com.swiftdrop.backend.modules.webhook.dto.WebhookDeliveryResponse;
import com.swiftdrop.backend.modules.webhook.entity.WebhookDeliveryStatus;
import com.swiftdrop.backend.modules.webhook.service.WebhookDeliveryService;

import lombok.RequiredArgsConstructor;

/**
 * Delivery-attempts API: the webhook delivery log, plus a manual retry for deliveries
 * that failed every attempt. Merchants see only their own deliveries; admins and
 * dispatchers see all of them.
 */
@RestController
@RequiredArgsConstructor
public class WebhookDeliveryController {

    public static final String MERCHANT_PATH = "/api/v1/merchant/webhooks/deliveries";
    public static final String ADMIN_PATH = "/api/v1/admin/webhooks/deliveries";
    static final int MAX_PAGE_SIZE = 100;

    private final WebhookDeliveryService deliveryService;

    @GetMapping(MERCHANT_PATH)
    @PreAuthorize("hasRole('MERCHANT')")
    public PageResponse<WebhookDeliveryResponse> listMine(
            Principal principal,
            @RequestParam(required = false) WebhookDeliveryStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(deliveryService.listForMerchant(principal.getName(), status, pageOf(page, size)),
                WebhookDeliveryResponse::from);
    }

    @GetMapping(MERCHANT_PATH + "/{id}")
    @PreAuthorize("hasRole('MERCHANT')")
    public WebhookDeliveryResponse getMine(Principal principal, @PathVariable UUID id) {
        return WebhookDeliveryResponse.from(deliveryService.getForMerchant(principal.getName(), id));
    }

    @PostMapping(MERCHANT_PATH + "/{id}/retry")
    @PreAuthorize("hasRole('MERCHANT')")
    public WebhookDeliveryResponse retryMine(Principal principal, @PathVariable UUID id) {
        return WebhookDeliveryResponse.from(deliveryService.retryForMerchant(principal.getName(), id));
    }

    @GetMapping(ADMIN_PATH)
    @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")
    public PageResponse<WebhookDeliveryResponse> listAll(
            @RequestParam(required = false) UUID merchantId,
            @RequestParam(required = false) WebhookDeliveryStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(deliveryService.listAll(merchantId, status, pageOf(page, size)),
                WebhookDeliveryResponse::from);
    }

    @GetMapping(ADMIN_PATH + "/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")
    public WebhookDeliveryResponse get(@PathVariable UUID id) {
        return WebhookDeliveryResponse.from(deliveryService.get(id));
    }

    @PostMapping(ADMIN_PATH + "/{id}/retry")
    @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")
    public WebhookDeliveryResponse retry(@PathVariable UUID id) {
        return WebhookDeliveryResponse.from(deliveryService.retryAsAdmin(id));
    }

    /** Newest first; out-of-range values are clamped instead of rejected. */
    private static Pageable pageOf(int page, int size) {
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        return PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
