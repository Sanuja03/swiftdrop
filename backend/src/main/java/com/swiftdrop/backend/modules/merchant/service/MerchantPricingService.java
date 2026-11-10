package com.swiftdrop.backend.modules.merchant.service;

import com.swiftdrop.backend.modules.merchant.dto.FeeCalculationRequest;
import com.swiftdrop.backend.modules.merchant.dto.FeeCalculationResponse;
import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Strategy-based pricing service calculating delivery fees and estimates
 * adhering to SwiftDrop pricing rules.
 */
@Service
public class MerchantPricingService {

    public FeeCalculationResponse calculateFee(FeeCalculationRequest request) {
        ServiceType service = request.getServiceType();
        BigDecimal weight = request.getWeightKg();

        BigDecimal baseFee;
        BigDecimal perKgRate;
        String estimatedTime;

        switch (service) {
            case EXPRESS -> {
                baseFee = new BigDecimal("900.00");
                perKgRate = new BigDecimal("25.00");
                estimatedTime = "Next day (within 24 hours)";
            }
            case SAME_DAY -> {
                baseFee = new BigDecimal("1400.00");
                perKgRate = new BigDecimal("35.00");
                estimatedTime = "Same day (within 6 hours)";
            }
            case STANDARD -> {
                baseFee = new BigDecimal("500.00");
                perKgRate = new BigDecimal("18.00");
                estimatedTime = "1 - 3 business days";
            }
            default -> throw new IllegalArgumentException("Unknown service type: " + service);
        }

        // Additional charge for weight beyond 1.0 kg
        BigDecimal weightCharge = BigDecimal.ZERO;
        if (weight.compareTo(BigDecimal.ONE) > 0) {
            BigDecimal extraWeight = weight.subtract(BigDecimal.ONE);
            weightCharge = extraWeight.multiply(perKgRate).setScale(2, RoundingMode.HALF_UP);
        }

        // Zone surcharge if pickup and dropoff cities are different
        BigDecimal zoneSurcharge = BigDecimal.ZERO;
        if (request.getPickupCity() != null && request.getDropoffCity() != null
                && !request.getPickupCity().trim().equalsIgnoreCase(request.getDropoffCity().trim())) {
            zoneSurcharge = new BigDecimal("90.00");
        }

        BigDecimal totalFee = baseFee.add(weightCharge).add(zoneSurcharge).setScale(2, RoundingMode.HALF_UP);

        return FeeCalculationResponse.builder()
                .serviceType(service)
                .weightKg(weight)
                .baseFee(baseFee)
                .weightCharge(weightCharge)
                .zoneSurcharge(zoneSurcharge)
                .totalFee(totalFee)
                .currency("LKR")
                .estimatedDeliveryTime(estimatedTime)
                .build();
    }

    public Instant estimateDeliveryDate(ServiceType service) {
        Instant now = Instant.now();
        return switch (service) {
            case SAME_DAY -> now.plus(6, ChronoUnit.HOURS);
            case EXPRESS -> now.plus(1, ChronoUnit.DAYS);
            case STANDARD -> now.plus(3, ChronoUnit.DAYS);
        };
    }
}

