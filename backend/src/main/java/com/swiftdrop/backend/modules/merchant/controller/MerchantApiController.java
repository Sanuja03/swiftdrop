package com.swiftdrop.backend.modules.merchant.controller;

import com.swiftdrop.backend.modules.merchant.dto.CreateMerchantOrderRequest;
import com.swiftdrop.backend.modules.merchant.dto.FeeCalculationRequest;
import com.swiftdrop.backend.modules.merchant.dto.FeeCalculationResponse;
import com.swiftdrop.backend.modules.merchant.dto.MerchantOrderResponse;
import com.swiftdrop.backend.modules.merchant.entity.Merchant;
import com.swiftdrop.backend.modules.merchant.exception.InvalidApiKeyException;
import com.swiftdrop.backend.modules.merchant.security.MerchantApiKeyAuthFilter;
import com.swiftdrop.backend.modules.merchant.service.MerchantOrderService;
import com.swiftdrop.backend.modules.merchant.service.MerchantPricingService;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * External REST API for Merchant platforms & automated integrations.
 * Secured with API Key ('X-API-KEY' header) or JWT bearer tokens.
 */
@RestController
@RequestMapping("/api/v1/merchant/api/v1")
public class MerchantApiController {

    private static final Logger log = LoggerFactory.getLogger(MerchantApiController.class);

    private final MerchantOrderService orderService;
    private final MerchantPricingService pricingService;

    public MerchantApiController(MerchantOrderService orderService, MerchantPricingService pricingService) {
        this.orderService = orderService;
        this.pricingService = pricingService;
    }

    @PostMapping("/orders")
    public ResponseEntity<MerchantOrderResponse> createOrder(
            HttpServletRequest request,
            @Valid @RequestBody CreateMerchantOrderRequest orderRequest) {

        Merchant merchant = getAuthenticatedMerchant(request);
        MerchantOrderResponse response = orderService.createOrder(merchant, orderRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/orders/{trackingNumber}")
    public ResponseEntity<MerchantOrderResponse> getOrder(
            HttpServletRequest request,
            @PathVariable String trackingNumber) {

        Merchant merchant = getAuthenticatedMerchant(request);
        MerchantOrderResponse response = orderService.getOrder(merchant.getId(), trackingNumber);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/orders/{trackingNumber}/cancel")
    public ResponseEntity<MerchantOrderResponse> cancelOrder(
            HttpServletRequest request,
            @PathVariable String trackingNumber) {

        Merchant merchant = getAuthenticatedMerchant(request);
        MerchantOrderResponse response = orderService.cancelOrder(merchant.getId(), trackingNumber);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/orders")
    public ResponseEntity<List<MerchantOrderResponse>> listOrders(
            HttpServletRequest request,
            @RequestParam(required = false) ParcelStatus status,
            @RequestParam(required = false) String search) {

        Merchant merchant = getAuthenticatedMerchant(request);
        List<MerchantOrderResponse> orders = orderService.getOrders(merchant.getId(), status, search);
        return ResponseEntity.ok(orders);
    }

    @PostMapping("/calculate-fee")
    public ResponseEntity<FeeCalculationResponse> calculateFee(
            @Valid @RequestBody FeeCalculationRequest feeRequest) {

        FeeCalculationResponse feeResponse = pricingService.calculateFee(feeRequest);
        return ResponseEntity.ok(feeResponse);
    }

    private Merchant getAuthenticatedMerchant(HttpServletRequest request) {
        Merchant merchant = (Merchant) request.getAttribute(MerchantApiKeyAuthFilter.AUTHENTICATED_MERCHANT_ATTR);
        if (merchant == null) {
            throw new InvalidApiKeyException("Valid merchant credentials required. Please provide a valid 'X-API-KEY' header.");
        }
        return merchant;
    }
}
