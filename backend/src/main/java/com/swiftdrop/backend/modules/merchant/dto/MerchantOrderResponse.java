package com.swiftdrop.backend.modules.merchant.dto;

import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class MerchantOrderResponse {
    private UUID id;
    private String trackingNumber;
    private UUID merchantId;
    private String senderName;
    private String pickupAddress;
    private String pickupCity;
    private String recipientName;
    private String recipientPhone;
    private String dropoffAddress;
    private String dropoffCity;
    private ServiceType serviceType;
    private BigDecimal weightKg;
    private BigDecimal fee;
    private ParcelStatus status;
    private String statusLabel;
    private Instant estimatedDeliveryAt;
    private Instant createdAt;
    private Instant updatedAt;
    private boolean cancellable;

    public MerchantOrderResponse() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private String trackingNumber;
        private UUID merchantId;
        private String senderName;
        private String pickupAddress;
        private String pickupCity;
        private String recipientName;
        private String recipientPhone;
        private String dropoffAddress;
        private String dropoffCity;
        private ServiceType serviceType;
        private BigDecimal weightKg;
        private BigDecimal fee;
        private ParcelStatus status;
        private String statusLabel;
        private Instant estimatedDeliveryAt;
        private Instant createdAt;
        private Instant updatedAt;
        private boolean cancellable;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder trackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; return this; }
        public Builder merchantId(UUID merchantId) { this.merchantId = merchantId; return this; }
        public Builder senderName(String senderName) { this.senderName = senderName; return this; }
        public Builder pickupAddress(String pickupAddress) { this.pickupAddress = pickupAddress; return this; }
        public Builder pickupCity(String pickupCity) { this.pickupCity = pickupCity; return this; }
        public Builder recipientName(String recipientName) { this.recipientName = recipientName; return this; }
        public Builder recipientPhone(String recipientPhone) { this.recipientPhone = recipientPhone; return this; }
        public Builder dropoffAddress(String dropoffAddress) { this.dropoffAddress = dropoffAddress; return this; }
        public Builder dropoffCity(String dropoffCity) { this.dropoffCity = dropoffCity; return this; }
        public Builder serviceType(ServiceType serviceType) { this.serviceType = serviceType; return this; }
        public Builder weightKg(BigDecimal weightKg) { this.weightKg = weightKg; return this; }
        public Builder fee(BigDecimal fee) { this.fee = fee; return this; }
        public Builder status(ParcelStatus status) { this.status = status; return this; }
        public Builder statusLabel(String statusLabel) { this.statusLabel = statusLabel; return this; }
        public Builder estimatedDeliveryAt(Instant estimatedDeliveryAt) { this.estimatedDeliveryAt = estimatedDeliveryAt; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }
        public Builder cancellable(boolean cancellable) { this.cancellable = cancellable; return this; }

        public MerchantOrderResponse build() {
            MerchantOrderResponse r = new MerchantOrderResponse();
            r.id = this.id;
            r.trackingNumber = this.trackingNumber;
            r.merchantId = this.merchantId;
            r.senderName = this.senderName;
            r.pickupAddress = this.pickupAddress;
            r.pickupCity = this.pickupCity;
            r.recipientName = this.recipientName;
            r.recipientPhone = this.recipientPhone;
            r.dropoffAddress = this.dropoffAddress;
            r.dropoffCity = this.dropoffCity;
            r.serviceType = this.serviceType;
            r.weightKg = this.weightKg;
            r.fee = this.fee;
            r.status = this.status;
            r.statusLabel = this.statusLabel;
            r.estimatedDeliveryAt = this.estimatedDeliveryAt;
            r.createdAt = this.createdAt;
            r.updatedAt = this.updatedAt;
            r.cancellable = this.cancellable;
            return r;
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }

    public UUID getMerchantId() { return merchantId; }
    public void setMerchantId(UUID merchantId) { this.merchantId = merchantId; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getPickupAddress() { return pickupAddress; }
    public void setPickupAddress(String pickupAddress) { this.pickupAddress = pickupAddress; }

    public String getPickupCity() { return pickupCity; }
    public void setPickupCity(String pickupCity) { this.pickupCity = pickupCity; }

    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }

    public String getRecipientPhone() { return recipientPhone; }
    public void setRecipientPhone(String recipientPhone) { this.recipientPhone = recipientPhone; }

    public String getDropoffAddress() { return dropoffAddress; }
    public void setDropoffAddress(String dropoffAddress) { this.dropoffAddress = dropoffAddress; }

    public String getDropoffCity() { return dropoffCity; }
    public void setDropoffCity(String dropoffCity) { this.dropoffCity = dropoffCity; }

    public ServiceType getServiceType() { return serviceType; }
    public void setServiceType(ServiceType serviceType) { this.serviceType = serviceType; }

    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }

    public BigDecimal getFee() { return fee; }
    public void setFee(BigDecimal fee) { this.fee = fee; }

    public ParcelStatus getStatus() { return status; }
    public void setStatus(ParcelStatus status) { this.status = status; }

    public String getStatusLabel() { return statusLabel; }
    public void setStatusLabel(String statusLabel) { this.statusLabel = statusLabel; }

    public Instant getEstimatedDeliveryAt() { return estimatedDeliveryAt; }
    public void setEstimatedDeliveryAt(Instant estimatedDeliveryAt) { this.estimatedDeliveryAt = estimatedDeliveryAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public boolean isCancellable() { return cancellable; }
    public void setCancellable(boolean cancellable) { this.cancellable = cancellable; }
}
