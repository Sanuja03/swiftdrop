package com.swiftdrop.backend.modules.assistant.service.impl;

import com.swiftdrop.backend.modules.assistant.dto.CustomerParcelSummary;
import com.swiftdrop.backend.modules.assistant.dto.EstimateFeeToolRequest;
import com.swiftdrop.backend.modules.assistant.dto.EstimateFeeToolResult;
import com.swiftdrop.backend.modules.assistant.dto.TrackParcelResult;
import com.swiftdrop.backend.modules.assistant.service.AssistantToolService;
import com.swiftdrop.backend.modules.auth.entity.AccountStatus;
import com.swiftdrop.backend.modules.auth.entity.User;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.parcel.entity.ParcelSize;
import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.parcel.exception.ParcelNotFoundException;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;
import com.swiftdrop.backend.modules.pricing.dto.EstimateRequest;
import com.swiftdrop.backend.modules.pricing.dto.FeeBreakdown;
import com.swiftdrop.backend.modules.pricing.exception.PricingException;
import com.swiftdrop.backend.modules.pricing.service.PricingService;
import com.swiftdrop.backend.modules.tracking.dto.PublicTrackingResponse;
import com.swiftdrop.backend.modules.tracking.service.TrackingQueryService;
import com.swiftdrop.backend.modules.zone.entity.Zone;
import com.swiftdrop.backend.modules.zone.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantToolServiceImpl implements AssistantToolService {

    private final TrackingQueryService trackingQueryService;
    private final PricingService pricingService;
    private final ZoneRepository zoneRepository;
    private final ParcelRepository parcelRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public TrackParcelResult trackParcel(String trackingNumber) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            return TrackParcelResult.failure("", "Please provide a valid parcel tracking number.");
        }

        String trimmed = trackingNumber.trim();
        try {
            PublicTrackingResponse response = trackingQueryService.getPublicTracking(trimmed);
            return TrackParcelResult.fromResponse(response);
        } catch (ParcelNotFoundException ex) {
            log.debug("Parcel not found for assistant tracking query: [{}]", trimmed);
            return TrackParcelResult.notFound(trimmed);
        } catch (IllegalArgumentException ex) {
            return TrackParcelResult.failure(trimmed, ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected error tracking parcel [{}] for assistant query", trimmed, ex);
            return TrackParcelResult.failure(trimmed, "Unable to retrieve tracking information at this time. Please try again later.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public EstimateFeeToolResult estimateFee(EstimateFeeToolRequest request) {
        if (request == null) {
            return EstimateFeeToolResult.builder()
                    .success(false)
                    .message("Please provide delivery details: pickup city, destination city, and weight.")
                    .missingFields(List.of("pickupCity", "dropoffCity", "weightKg"))
                    .build();
        }

        List<String> missing = new ArrayList<>();
        if (request.getPickupCity() == null || request.getPickupCity().isBlank()) {
            missing.add("pickupCity");
        }
        if (request.getDropoffCity() == null || request.getDropoffCity().isBlank()) {
            missing.add("dropoffCity");
        }
        if (request.getWeightKg() == null) {
            missing.add("weightKg");
        }

        List<Zone> activeZones = zoneRepository.findAllByActiveTrueOrderByNameAsc();
        List<String> availableZoneNames = activeZones.stream().map(Zone::getName).toList();

        if (!missing.isEmpty()) {
            String missingStr = String.join(", ", missing);
            return EstimateFeeToolResult.builder()
                    .success(false)
                    .message("Missing required pricing input(s): " + missingStr + ".")
                    .missingFields(missing)
                    .availableZones(availableZoneNames)
                    .build();
        }

        BigDecimal weight = request.getWeightKg();
        if (weight.compareTo(BigDecimal.ZERO) <= 0) {
            return EstimateFeeToolResult.builder()
                    .success(false)
                    .message("Parcel weight must be greater than 0 kg.")
                    .build();
        }
        if (weight.compareTo(new BigDecimal("30")) > 0) {
            return EstimateFeeToolResult.builder()
                    .success(false)
                    .message("Maximum parcel weight is 30 kg.")
                    .build();
        }

        Zone pickupZone = findZone(activeZones, request.getPickupCity());
        if (pickupZone == null) {
            return EstimateFeeToolResult.builder()
                    .success(false)
                    .message("Unknown pickup city: '" + request.getPickupCity() + "'. Available cities: " + String.join(", ", availableZoneNames) + ".")
                    .availableZones(availableZoneNames)
                    .build();
        }

        Zone dropoffZone = findZone(activeZones, request.getDropoffCity());
        if (dropoffZone == null) {
            return EstimateFeeToolResult.builder()
                    .success(false)
                    .message("Unknown destination city: '" + request.getDropoffCity() + "'. Available cities: " + String.join(", ", availableZoneNames) + ".")
                    .availableZones(availableZoneNames)
                    .build();
        }

        ServiceType serviceType = parseServiceType(request.getServiceType());
        ParcelSize parcelSize = resolveParcelSize(request.getParcelSize(), weight);

        try {
            EstimateRequest estimateRequest = new EstimateRequest(
                    pickupZone.getId(),
                    dropoffZone.getId(),
                    parcelSize,
                    weight,
                    serviceType
            );

            FeeBreakdown breakdown = pricingService.estimate(estimateRequest);

            String message = String.format("Delivery fee for %.1f kg from %s to %s (%s) is %s %.2f.",
                    weight.doubleValue(), pickupZone.getName(), dropoffZone.getName(),
                    serviceType.getLabel(), breakdown.currency(), breakdown.totalFee());

            return EstimateFeeToolResult.builder()
                    .success(true)
                    .baseFee(breakdown.baseFee())
                    .weightCharge(breakdown.weightCharge())
                    .zoneCharge(breakdown.zoneCharge())
                    .totalFee(breakdown.totalFee())
                    .currency(breakdown.currency())
                    .estimatedDeliveryAt(breakdown.estimatedDeliveryAt())
                    .pickupZone(pickupZone.getName())
                    .dropoffZone(dropoffZone.getName())
                    .serviceType(serviceType.name())
                    .parcelSize(parcelSize.name())
                    .message(message)
                    .build();
        } catch (PricingException ex) {
            log.warn("Pricing calculation failed: {}", ex.getMessage());
            return EstimateFeeToolResult.builder()
                    .success(false)
                    .message("Could not calculate fee: " + ex.getMessage())
                    .build();
        } catch (Exception ex) {
            log.error("Unexpected pricing calculation error", ex);
            return EstimateFeeToolResult.builder()
                    .success(false)
                    .message("Pricing calculation temporarily unavailable.")
                    .build();
        }
    }

    @Override
    public List<CustomerParcelSummary> listMyParcels() {
        UUID customerId = resolveAuthenticatedCustomerId();
        return listMyParcels(customerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerParcelSummary> listMyParcels(UUID customerId) {
        if (customerId == null) {
            throw new AccessDeniedException("Authentication required to access personal parcels");
        }

        List<Parcel> parcels = parcelRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
        return parcels.stream()
                .map(CustomerParcelSummary::fromEntity)
                .toList();
    }

    private UUID resolveAuthenticatedCustomerId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new AccessDeniedException("Authentication required to access personal parcels");
        }

        String email = auth.getName();
        if (email == null || email.isBlank()) {
            throw new AccessDeniedException("No authenticated customer email found");
        }

        User user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user account not found"));

        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new AccessDeniedException("User account is not active");
        }

        return user.getId();
    }

    private Zone findZone(List<Zone> zones, String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String clean = input.trim();
        for (Zone zone : zones) {
            if (zone.getName().equalsIgnoreCase(clean) || zone.getCode().equalsIgnoreCase(clean)) {
                return zone;
            }
            if (clean.equalsIgnoreCase(zone.getId().toString())) {
                return zone;
            }
        }
        return null;
    }

    private ServiceType parseServiceType(String input) {
        if (input == null || input.isBlank()) {
            return ServiceType.STANDARD;
        }
        String clean = input.trim().toUpperCase().replace(" ", "_").replace("-", "_");
        try {
            return ServiceType.valueOf(clean);
        } catch (IllegalArgumentException ex) {
            if (clean.contains("SAME")) return ServiceType.SAME_DAY;
            if (clean.contains("EXPRESS") || clean.contains("NEXT")) return ServiceType.EXPRESS;
            return ServiceType.STANDARD;
        }
    }

    private ParcelSize resolveParcelSize(String userProvidedSize, BigDecimal weight) {
        if (userProvidedSize != null && !userProvidedSize.isBlank()) {
            try {
                ParcelSize size = ParcelSize.valueOf(userProvidedSize.trim().toUpperCase());
                if (weight.compareTo(size.minWeight()) >= 0 && weight.compareTo(size.maxWeight()) <= 0) {
                    return size;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (weight.compareTo(new BigDecimal("0.5")) <= 0) {
            return ParcelSize.DOCUMENTS;
        } else if (weight.compareTo(new BigDecimal("2")) <= 0) {
            return ParcelSize.SMALL;
        } else if (weight.compareTo(new BigDecimal("10")) <= 0) {
            return ParcelSize.MEDIUM;
        } else {
            return ParcelSize.LARGE;
        }
    }
}
