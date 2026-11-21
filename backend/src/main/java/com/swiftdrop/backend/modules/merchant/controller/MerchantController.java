package com.swiftdrop.backend.modules.merchant.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.backend.modules.merchant.dto.CreateMerchantOrderRequest;
import com.swiftdrop.backend.modules.merchant.dto.FeeCalculationRequest;
import com.swiftdrop.backend.modules.merchant.dto.FeeCalculationResponse;
import com.swiftdrop.backend.modules.merchant.dto.MerchantOrderResponse;
import com.swiftdrop.backend.modules.merchant.dto.MerchantProfileResponse;
import com.swiftdrop.backend.modules.merchant.dto.MerchantRegisterRequest;
import com.swiftdrop.backend.modules.merchant.dto.MerchantStatsResponse;
import com.swiftdrop.backend.modules.merchant.dto.MerchantWebhookLogDto;
import com.swiftdrop.backend.modules.merchant.dto.WebhookConfigRequest;
import com.swiftdrop.backend.modules.merchant.dto.WebhookTestRequest;
import com.swiftdrop.backend.modules.merchant.dto.WebhookTestResponse;
import com.swiftdrop.backend.modules.merchant.entity.Merchant;
import com.swiftdrop.backend.modules.merchant.security.CurrentMerchantProvider;
import com.swiftdrop.backend.modules.merchant.service.MerchantOrderService;
import com.swiftdrop.backend.modules.merchant.service.MerchantPricingService;
import com.swiftdrop.backend.modules.merchant.service.MerchantService;
import com.swiftdrop.backend.modules.merchant.service.MerchantWebhookService;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;

import jakarta.validation.Valid;

/**
 * Merchant portal (JWT, ROLE_MERCHANT; see MerchantSecurityConfig). Every endpoint acts for the
 * signed-in user's own merchant profile, resolved by CurrentMerchantProvider. No endpoint accepts
 * a merchant id from the client.
 */
@RestController
@RequestMapping("/api/v1/merchant")
public class MerchantController {

    private final MerchantService merchantService;
    private final MerchantOrderService orderService;
    private final MerchantPricingService pricingService;
    private final MerchantWebhookService webhookService;
    private final CurrentMerchantProvider currentMerchantProvider;

    public MerchantController(MerchantService merchantService,
                              MerchantOrderService orderService,
                              MerchantPricingService pricingService,
                              MerchantWebhookService webhookService,
                              CurrentMerchantProvider currentMerchantProvider) {
        this.merchantService = merchantService;
        this.orderService = orderService;
        this.pricingService = pricingService;
        this.webhookService = webhookService;
        this.currentMerchantProvider = currentMerchantProvider;
    }

    /** Creates the merchant profile (PENDING) for the signed-in merchant user's own account. */
    @PostMapping("/register")
    public ResponseEntity<MerchantProfileResponse> register(@Valid @RequestBody MerchantRegisterRequest request) {
        var user = currentMerchantProvider.currentUser();
        return ResponseEntity.status(HttpStatus.CREATED).body(merchantService.registerMerchant(user.getId(), request));
    }

    @GetMapping({"/profile", "/me"})
    public ResponseEntity<MerchantProfileResponse> getProfile() {
        return ResponseEntity.ok(merchantService.mapToProfile(currentMerchant()));
    }

    @GetMapping("/stats")
    public ResponseEntity<MerchantStatsResponse> getStats() {
        return ResponseEntity.ok(merchantService.getStats(currentMerchant().getId()));
    }

    @GetMapping("/orders")
    public ResponseEntity<List<MerchantOrderResponse>> getOrders(
            @RequestParam(required = false) ParcelStatus status,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(orderService.getOrders(currentMerchant().getId(), status, search));
    }

    @GetMapping("/orders/{trackingNumber}")
    public ResponseEntity<MerchantOrderResponse> getOrder(@PathVariable String trackingNumber) {
        return ResponseEntity.ok(orderService.getOrder(currentMerchant().getId(), trackingNumber));
    }

    @PostMapping("/orders")
    public ResponseEntity<MerchantOrderResponse> createOrder(@Valid @RequestBody CreateMerchantOrderRequest orderRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(currentMerchant(), orderRequest));
    }

    @PostMapping("/orders/{trackingNumber}/cancel")
    public ResponseEntity<MerchantOrderResponse> cancelOrder(@PathVariable String trackingNumber) {
        return ResponseEntity.ok(orderService.cancelOrder(currentMerchant(), trackingNumber));
    }

    @PostMapping("/calculate-fee")
    public ResponseEntity<FeeCalculationResponse> calculateFee(@Valid @RequestBody FeeCalculationRequest feeRequest) {
        return ResponseEntity.ok(pricingService.calculateFee(feeRequest));
    }

    @PutMapping("/webhook")
    public ResponseEntity<MerchantProfileResponse> updateWebhook(@Valid @RequestBody WebhookConfigRequest webhookRequest) {
        return ResponseEntity.ok(merchantService.updateWebhookConfig(currentMerchant().getId(), webhookRequest));
    }

    @PostMapping("/webhook/test")
    public ResponseEntity<WebhookTestResponse> testWebhook(@RequestBody(required = false) WebhookTestRequest testRequest) {
        String targetUrl = testRequest != null ? testRequest.getWebhookUrl() : null;
        return ResponseEntity.ok(webhookService.testWebhook(currentMerchant().getId(), targetUrl));
    }

    @GetMapping("/webhook/logs")
    public ResponseEntity<List<MerchantWebhookLogDto>> getWebhookLogs() {
        return ResponseEntity.ok(webhookService.getRecentLogs(currentMerchant().getId()));
    }

    /** Returns the new key once; it is shown to the merchant and never logged. */
    @PostMapping({"/api-key/regenerate", "/api-key/rotate"})
    public ResponseEntity<Map<String, String>> regenerateApiKey() {
        return ResponseEntity.ok(Map.of("apiKey", merchantService.regenerateApiKey(currentMerchant().getId())));
    }

    private Merchant currentMerchant() {
        return currentMerchantProvider.currentMerchant();
    }
}
