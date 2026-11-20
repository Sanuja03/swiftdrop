package com.swiftdrop.backend.modules.merchant.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.backend.modules.booking.service.TrackingNumberGenerator;
import com.swiftdrop.backend.modules.dispatch.entity.AssignmentStatus;
import com.swiftdrop.backend.modules.dispatch.repository.DispatchCourierAssignmentRepository;
import com.swiftdrop.backend.modules.dispatch.service.DispatchService;
import com.swiftdrop.backend.modules.merchant.dto.CreateMerchantOrderRequest;
import com.swiftdrop.backend.modules.merchant.dto.MerchantOrderResponse;
import com.swiftdrop.backend.modules.merchant.entity.Merchant;
import com.swiftdrop.backend.modules.merchant.exception.MerchantNotApprovedException;
import com.swiftdrop.backend.modules.merchant.exception.OrderCannotBeCancelledException;
import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.parcel.exception.ParcelNotFoundException;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;
import com.swiftdrop.backend.modules.parcel.service.ParcelLookupService;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.modules.tracking.service.ParcelStatusService;

/**
 * Merchant orders. Orders are ordinary parcels (Parcel.builder()...merchantId(..)), priced by the
 * shared PricingService and numbered by the shared TrackingNumberGenerator. Every status change,
 * including the initial CREATED entry and a merchant cancel, goes through ParcelStatusService, so
 * history, live tracking, outbox events and merchant webhooks all fire exactly as for any parcel.
 */
@Service
public class MerchantOrderService {

    private static final Logger log = LoggerFactory.getLogger(MerchantOrderService.class);
    private static final Set<AssignmentStatus> ACTIVE_ASSIGNMENT = Set.of(AssignmentStatus.ASSIGNED, AssignmentStatus.ACCEPTED);
    static final String CANCEL_NOTE = "Cancelled by merchant";

    private final ParcelRepository parcelRepository;
    private final MerchantPricingService pricingService;
    private final TrackingNumberGenerator trackingNumberGenerator;
    private final ParcelStatusService parcelStatusService;
    private final DispatchCourierAssignmentRepository assignmentRepository;
    private final DispatchService dispatchService;

    public MerchantOrderService(ParcelRepository parcelRepository,
                                MerchantPricingService pricingService,
                                TrackingNumberGenerator trackingNumberGenerator,
                                ParcelStatusService parcelStatusService,
                                DispatchCourierAssignmentRepository assignmentRepository,
                                DispatchService dispatchService) {
        this.parcelRepository = parcelRepository;
        this.pricingService = pricingService;
        this.trackingNumberGenerator = trackingNumberGenerator;
        this.parcelStatusService = parcelStatusService;
        this.assignmentRepository = assignmentRepository;
        this.dispatchService = dispatchService;
    }

    /** @throws MerchantNotApprovedException unless the merchant is APPROVED */
    @Transactional
    public MerchantOrderResponse createOrder(Merchant merchant, CreateMerchantOrderRequest request) {
        if (!merchant.isApproved()) {
            throw new MerchantNotApprovedException("Only approved merchants can book deliveries.");
        }
        var quote = pricingService.quote(request.getServiceType(), request.getWeightKg(),
                request.getPickupCity(), request.getDropoffCity());
        var fee = quote.fee();
        String senderName = request.getSenderName() != null && !request.getSenderName().isBlank()
                ? request.getSenderName().strip()
                : merchant.getBusinessName();

        String trackingNumber = trackingNumberGenerator.nextMerchantTrackingNumber();
        if (trackingNumber == null) {
            trackingNumber = trackingNumberGenerator.next();
        }

        Parcel parcel = parcelRepository.save(Parcel.builder()
                .trackingNumber(trackingNumber)
                .merchantId(merchant.getId())
                .senderName(senderName)
                .pickupAddress(request.getPickupAddress().strip())
                .pickupCity(quote.pickupZone().getName())
                .pickupZoneId(quote.pickupZone().getId())
                .recipientName(request.getRecipientName().strip())
                .recipientPhone(request.getRecipientPhone().strip())
                .dropoffAddress(request.getDropoffAddress().strip())
                .dropoffCity(quote.dropoffZone().getName())
                .dropoffZoneId(quote.dropoffZone().getId())
                .parcelSize(quote.parcelSize())
                .serviceType(request.getServiceType())
                .weightKg(request.getWeightKg())
                .baseFee(fee.baseFee())
                .weightCharge(fee.weightCharge())
                .zoneCharge(fee.zoneCharge())
                .fee(fee.totalFee())
                .status(ParcelStatus.CREATED)
                .estimatedDeliveryAt(fee.estimatedDeliveryAt())
                .build());

        // First timeline entry + ParcelStatusChangedEvent (outbox event, webhook, live update).
        parcelStatusService.recordCreation(parcel, merchant.getUserId());
        log.info("Merchant {} created order {}", merchant.getId(), parcel.getTrackingNumber());
        return mapToResponse(parcel);
    }

    @Transactional(readOnly = true)
    public List<MerchantOrderResponse> getOrders(UUID merchantId, ParcelStatus statusFilter, String search) {
        List<Parcel> parcels = statusFilter != null
                ? parcelRepository.findByMerchantIdAndStatusOrderByCreatedAtDesc(merchantId, statusFilter)
                : parcelRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId);

        if (search != null && !search.isBlank()) {
            String q = search.strip().toLowerCase();
            parcels = parcels.stream().filter(p ->
                    p.getTrackingNumber().toLowerCase().contains(q)
                            || p.getRecipientName().toLowerCase().contains(q)
                            || (p.getRecipientPhone() != null && p.getRecipientPhone().contains(q))
                            || p.getDropoffCity().toLowerCase().contains(q)
            ).toList();
        }
        return parcels.stream().map(this::mapToResponse).toList();
    }

    /** @throws ParcelNotFoundException if this merchant has no parcel with that tracking number */
    @Transactional(readOnly = true)
    public MerchantOrderResponse getOrder(UUID merchantId, String trackingNumber) {
        return mapToResponse(ownParcel(merchantId, trackingNumber));
    }

    /**
     * Cancels one of the merchant's own parcels while it is still CREATED, releasing any active
     * courier assignment first.
     *
     * @throws ParcelNotFoundException if this merchant has no parcel with that tracking number
     * @throws OrderCannotBeCancelledException if the parcel is no longer CREATED
     */
    @Transactional
    public MerchantOrderResponse cancelOrder(Merchant merchant, String trackingNumber) {
        Parcel owned = ownParcel(merchant.getId(), trackingNumber);
        // Lock the row so a courier pickup and a cancel cannot both succeed.
        Parcel parcel = parcelRepository.lockById(owned.getId()).orElseThrow(() -> new ParcelNotFoundException(trackingNumber));
        if (parcel.getStatus() != ParcelStatus.CREATED) {
            throw new OrderCannotBeCancelledException(parcel.getTrackingNumber(), parcel.getStatus());
        }

        assignmentRepository.findByParcelIdAndStatusIn(parcel.getId(), ACTIVE_ASSIGNMENT)
                .ifPresent(assignment -> dispatchService.cancelAssignment(assignment.getId(), CANCEL_NOTE));

        parcelStatusService.updateStatus(parcel.getId(), ParcelStatus.CANCELLED, CANCEL_NOTE, merchant.getUserId());
        log.info("Merchant {} cancelled order {}", merchant.getId(), parcel.getTrackingNumber());
        return mapToResponse(parcel);
    }

    private Parcel ownParcel(UUID merchantId, String trackingNumber) {
        String normalised = ParcelLookupService.normalise(trackingNumber);
        return parcelRepository.findByTrackingNumberAndMerchantId(normalised, merchantId)
                .orElseThrow(() -> new ParcelNotFoundException(normalised));
    }

    public MerchantOrderResponse mapToResponse(Parcel p) {
        return MerchantOrderResponse.builder()
                .id(p.getId())
                .trackingNumber(p.getTrackingNumber())
                .merchantId(p.getMerchantId())
                .senderName(p.getSenderName())
                .pickupAddress(p.getPickupAddress())
                .pickupCity(p.getPickupCity())
                .recipientName(p.getRecipientName())
                .recipientPhone(p.getRecipientPhone())
                .dropoffAddress(p.getDropoffAddress())
                .dropoffCity(p.getDropoffCity())
                .serviceType(p.getServiceType())
                .weightKg(p.getWeightKg())
                .fee(p.getFee())
                .status(p.getStatus())
                .statusLabel(p.getStatus().getLabel())
                .estimatedDeliveryAt(p.getEstimatedDeliveryAt())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .cancellable(p.getStatus() == ParcelStatus.CREATED)
                .build();
    }
}
