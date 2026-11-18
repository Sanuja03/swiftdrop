package com.swiftdrop.backend.modules.tracking.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;
import com.swiftdrop.backend.modules.tracking.dto.CustomerShipmentResponse;

import lombok.RequiredArgsConstructor;

/**
 * Read side for the customer's own shipment lists (dashboard, My Shipments, Delivery History).
 */
@Service
@RequiredArgsConstructor
public class CustomerShipmentQueryService {

    private final ParcelRepository parcelRepository;

    /** Every parcel booked by this customer, newest booking first. */
    @Transactional(readOnly = true)
    public List<CustomerShipmentResponse> getShipments(UUID customerId) {
        return parcelRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(CustomerShipmentResponse::from)
                .toList();
    }
}
