package com.swiftdrop.backend.modules.courier.controller;

import com.swiftdrop.backend.modules.courier.service.CourierService;
import com.swiftdrop.backend.modules.courier.dto.CourierDeliveryResponse;
import com.swiftdrop.backend.modules.courier.dto.CourierStatusUpdateRequest;
import com.swiftdrop.backend.modules.courier.service.CourierDeliveryService;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/v1/courier")
@RequiredArgsConstructor
public class CourierController {

    private final CourierService courierService;
    private final CourierDeliveryService deliveries;

    @GetMapping("/me")
    public CourierProfileResponse getCurrentCourier(Principal principal) {
        var courier = courierService.requireCurrentCourier(principal);
        return new CourierProfileResponse(courier.getId());
    }

    public record CourierProfileResponse(UUID courierId) {
    }

    @PostMapping("/parcels/{parcelId}/pickup")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void pickUp(@PathVariable UUID parcelId, Principal principal) {
        deliveries.updateStatus(parcelId, ParcelStatus.PICKED_UP, null, principal);
    }

    @GetMapping("/deliveries/today")
    public List<CourierDeliveryResponse> today(Principal principal) {
        return deliveries.today(principal);
    }

    @GetMapping("/parcels/{parcelId}")
    public CourierDeliveryResponse details(@PathVariable UUID parcelId, Principal principal) {
        return deliveries.details(parcelId, principal);
    }

    @PatchMapping("/parcels/{parcelId}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateStatus(@PathVariable UUID parcelId,
                             @Valid @RequestBody CourierStatusUpdateRequest request, Principal principal) {
        deliveries.updateStatus(parcelId, request.status(), request.note(), principal);
    }
}
