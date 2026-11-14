package com.swiftdrop.backend.modules.tracking.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.backend.common.security.CurrentCustomerProvider;
import com.swiftdrop.backend.modules.tracking.dto.CustomerShipmentResponse;
import com.swiftdrop.backend.modules.tracking.service.CustomerShipmentQueryService;

import lombok.RequiredArgsConstructor;

/**
 * The signed-in customer's own shipments. The customer always comes from the JWT,
 * never from the request, so nobody can list another customer's parcels.
 */
@RestController
@RequestMapping(CustomerShipmentController.BASE_PATH)
@RequiredArgsConstructor
public class CustomerShipmentController {

    public static final String BASE_PATH = "/api/v1/customer/parcels";

    private final CustomerShipmentQueryService customerShipmentQueryService;
    private final CurrentCustomerProvider currentCustomerProvider;

    @GetMapping
    public List<CustomerShipmentResponse> myShipments() {
        return customerShipmentQueryService.getShipments(currentCustomerProvider.currentCustomer().id());
    }
}
