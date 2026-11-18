package com.swiftdrop.backend.modules.receipt.pdf;

import java.math.BigDecimal;
import java.time.Instant;

import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.receipt.entity.Receipt;

/**
 * Everything printed on a receipt, kept separate from the JPA entities so
 * renderers do not depend on persistence.
 */
public record ReceiptData(
        String receiptNumber,
        Instant issuedAt,
        String trackingNumber,
        String senderName,
        String pickupAddress,
        String recipientName,
        String dropoffAddress,
        String serviceLabel,
        BigDecimal weightKg,
        String statusLabel,
        BigDecimal amount,
        String currency) {

    public static ReceiptData of(Receipt receipt, Parcel parcel) {
        return new ReceiptData(
                receipt.getReceiptNumber(),
                receipt.getIssuedAt(),
                parcel.getTrackingNumber(),
                parcel.getSenderName(),
                parcel.getPickupAddress() + ", " + parcel.getPickupCity(),
                parcel.getRecipientName(),
                parcel.getDropoffAddress() + ", " + parcel.getDropoffCity(),
                parcel.getServiceType().getLabel(),
                parcel.getWeightKg(),
                parcel.getStatus().getLabel(),
                receipt.getAmount(),
                receipt.getCurrency());
    }
}
