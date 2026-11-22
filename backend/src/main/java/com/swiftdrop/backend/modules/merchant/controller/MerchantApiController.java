package com.swiftdrop.backend.modules.merchant.controller;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.backend.modules.merchant.dto.CreateMerchantOrderRequest;
import com.swiftdrop.backend.modules.merchant.dto.FeeCalculationRequest;
import com.swiftdrop.backend.modules.merchant.dto.FeeCalculationResponse;
import com.swiftdrop.backend.modules.merchant.dto.MerchantOrderResponse;
import com.swiftdrop.backend.modules.merchant.security.CurrentMerchantProvider;
import com.swiftdrop.backend.modules.merchant.security.MerchantSecurityConfig;
import com.swiftdrop.backend.modules.merchant.service.MerchantOrderService;
import com.swiftdrop.backend.modules.merchant.service.MerchantPricingService;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;

import jakarta.validation.Valid;

/**
 * External REST API for merchant integrations, authenticated with the X-API-KEY header only
 * (see MerchantSecurityConfig). The merchant always comes from the API key.
 */
@RestController
@RequestMapping(MerchantSecurityConfig.API_PATH)
public class MerchantApiController {

    private final MerchantOrderService orderService;
    private final MerchantPricingService pricingService;
    private final CurrentMerchantProvider currentMerchantProvider;

    public MerchantApiController(MerchantOrderService orderService, MerchantPricingService pricingService,
                                 CurrentMerchantProvider currentMerchantProvider) {
        this.orderService = orderService;
        this.pricingService = pricingService;
        this.currentMerchantProvider = currentMerchantProvider;
    }

    private final Map<String, MerchantOrderResponse> idempotencyCache = new ConcurrentHashMap<>();

    @PostMapping("/orders")
    public ResponseEntity<MerchantOrderResponse> createOrder(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateMerchantOrderRequest orderRequest) {
        var merchant = currentMerchantProvider.currentMerchant();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String cacheKey = merchant.getId() + ":" + idempotencyKey.strip();
            MerchantOrderResponse cached = idempotencyCache.get(cacheKey);
            if (cached != null) {
                return ResponseEntity.ok(cached);
            }
            MerchantOrderResponse created = orderService.createOrder(merchant, orderRequest);
            idempotencyCache.put(cacheKey, created);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.createOrder(merchant, orderRequest));
    }

    @GetMapping("/orders/{trackingNumber}")
    public ResponseEntity<MerchantOrderResponse> getOrder(@PathVariable String trackingNumber) {
        return ResponseEntity.ok(orderService.getOrder(currentMerchantProvider.currentMerchant().getId(), trackingNumber));
    }

    @PostMapping("/orders/{trackingNumber}/cancel")
    public ResponseEntity<MerchantOrderResponse> cancelOrder(@PathVariable String trackingNumber) {
        return ResponseEntity.ok(orderService.cancelOrder(currentMerchantProvider.currentMerchant(), trackingNumber));
    }

    @GetMapping("/orders")
    public ResponseEntity<List<MerchantOrderResponse>> listOrders(
            @RequestParam(required = false) ParcelStatus status,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(orderService.getOrders(currentMerchantProvider.currentMerchant().getId(), status, search));
    }

    @PostMapping("/calculate-fee")
    public ResponseEntity<FeeCalculationResponse> calculateFee(@Valid @RequestBody FeeCalculationRequest feeRequest) {
        return ResponseEntity.ok(pricingService.calculateFee(feeRequest));
    }
}
