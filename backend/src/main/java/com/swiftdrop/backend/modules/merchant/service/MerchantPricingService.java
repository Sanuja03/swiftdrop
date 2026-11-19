package com.swiftdrop.backend.modules.merchant.service;

import java.math.BigDecimal;
import java.util.Arrays;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.backend.modules.merchant.dto.FeeCalculationRequest;
import com.swiftdrop.backend.modules.merchant.dto.FeeCalculationResponse;
import com.swiftdrop.backend.modules.merchant.exception.InvalidOrderException;
import com.swiftdrop.backend.modules.parcel.entity.ParcelSize;
import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.pricing.dto.EstimateRequest;
import com.swiftdrop.backend.modules.pricing.dto.FeeBreakdown;
import com.swiftdrop.backend.modules.pricing.exception.PricingException;
import com.swiftdrop.backend.modules.pricing.service.PricingService;
import com.swiftdrop.backend.modules.zone.entity.Zone;
import com.swiftdrop.backend.modules.zone.repository.ZoneRepository;

/**
 * Prices merchant orders. There is no merchant-specific fee formula: every fee comes from the
 * shared PricingService (the same strategies and admin-managed rules as customer bookings).
 * This class only adapts the merchant API's input (city or zone name, weight) to it.
 */
@Service
@Transactional(readOnly = true)
public class MerchantPricingService {

    private final PricingService pricingService;
    private final ZoneRepository zoneRepository;

    public MerchantPricingService(PricingService pricingService, ZoneRepository zoneRepository) {
        this.pricingService = pricingService;
        this.zoneRepository = zoneRepository;
    }

    /** A priced merchant order: the resolved zones, the parcel size used and the fee breakdown. */
    public record Quote(Zone pickupZone, Zone dropoffZone, ParcelSize parcelSize, FeeBreakdown fee) { }

    /**
     * @throws InvalidOrderException if a city is not an active zone, the weight is too high, or the
     *         service cannot be priced (for example Same Day across zones)
     */
    public Quote quote(ServiceType serviceType, BigDecimal weightKg, String pickupCity, String dropoffCity) {
        Zone pickup = zone(pickupCity, "pickup");
        Zone dropoff = zone(dropoffCity, "drop-off");
        ParcelSize size = sizeFor(weightKg);
        try {
            FeeBreakdown fee = pricingService.estimate(
                    new EstimateRequest(pickup.getId(), dropoff.getId(), size, weightKg, serviceType));
            return new Quote(pickup, dropoff, size, fee);
        } catch (PricingException | IllegalArgumentException ex) {
            throw new InvalidOrderException(ex.getMessage());
        }
    }

    public FeeCalculationResponse calculateFee(FeeCalculationRequest request) {
        Quote quote = quote(request.getServiceType(), request.getWeightKg(), request.getPickupCity(), request.getDropoffCity());
        FeeBreakdown fee = quote.fee();
        return FeeCalculationResponse.builder()
                .serviceType(request.getServiceType())
                .weightKg(request.getWeightKg())
                .baseFee(fee.baseFee())
                .weightCharge(fee.weightCharge())
                .zoneSurcharge(fee.zoneCharge())
                .totalFee(fee.totalFee())
                .currency(fee.currency())
                .estimatedDeliveryTime(request.getServiceType().getLabel())
                .build();
    }

    /** The smallest parcel size that fits the weight (the size bands are the booking module's). */
    static ParcelSize sizeFor(BigDecimal weightKg) {
        return Arrays.stream(new ParcelSize[] {ParcelSize.SMALL, ParcelSize.MEDIUM, ParcelSize.LARGE})
                .filter(size -> weightKg.compareTo(size.maxWeight()) <= 0)
                .findFirst()
                .orElseThrow(() -> new InvalidOrderException(
                        "Weight must be at most %s kg.".formatted(ParcelSize.LARGE.maxWeight())));
    }

    private Zone zone(String cityOrZone, String which) {
        if (cityOrZone == null || cityOrZone.isBlank()) {
            throw new InvalidOrderException("The %s city is required.".formatted(which));
        }
        String value = cityOrZone.strip();
        return zoneRepository.findFirstByNameIgnoreCaseAndActiveTrue(value)
                .or(() -> zoneRepository.findFirstByCodeIgnoreCaseAndActiveTrue(value))
                .orElseThrow(() -> new InvalidOrderException(
                        "We do not deliver to '%s' yet. Use one of the active delivery zones.".formatted(value)));
    }
}
