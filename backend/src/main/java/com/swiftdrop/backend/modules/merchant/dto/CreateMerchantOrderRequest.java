package com.swiftdrop.backend.modules.merchant.dto;

import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class CreateMerchantOrderRequest {

    private String senderName;

    @NotBlank(message = "Pickup address is required")
    private String pickupAddress;

    @NotBlank(message = "Pickup city is required")
    private String pickupCity;

    @NotBlank(message = "Recipient name is required")
    private String recipientName;

    @NotBlank(message = "Recipient contact phone is required")
    private String recipientPhone;

    @NotBlank(message = "Dropoff address is required")
    private String dropoffAddress;

    @NotBlank(message = "Dropoff city is required")
    private String dropoffCity;

    @NotNull(message = "Service type is required (STANDARD, EXPRESS, SAME_DAY)")
    private ServiceType serviceType;

    @NotNull(message = "Weight in kg is required")
    @DecimalMin(value = "0.1", message = "Weight must be at least 0.1 kg")
    private BigDecimal weightKg;

    public CreateMerchantOrderRequest() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String senderName;
        private String pickupAddress;
        private String pickupCity;
        private String recipientName;
        private String recipientPhone;
        private String dropoffAddress;
        private String dropoffCity;
        private ServiceType serviceType;
        private BigDecimal weightKg;

        public Builder senderName(String senderName) { this.senderName = senderName; return this; }
        public Builder pickupAddress(String pickupAddress) { this.pickupAddress = pickupAddress; return this; }
        public Builder pickupCity(String pickupCity) { this.pickupCity = pickupCity; return this; }
        public Builder recipientName(String recipientName) { this.recipientName = recipientName; return this; }
        public Builder recipientPhone(String recipientPhone) { this.recipientPhone = recipientPhone; return this; }
        public Builder dropoffAddress(String dropoffAddress) { this.dropoffAddress = dropoffAddress; return this; }
        public Builder dropoffCity(String dropoffCity) { this.dropoffCity = dropoffCity; return this; }
        public Builder serviceType(ServiceType serviceType) { this.serviceType = serviceType; return this; }
        public Builder weightKg(BigDecimal weightKg) { this.weightKg = weightKg; return this; }

        public CreateMerchantOrderRequest build() {
            CreateMerchantOrderRequest r = new CreateMerchantOrderRequest();
            r.senderName = this.senderName;
            r.pickupAddress = this.pickupAddress;
            r.pickupCity = this.pickupCity;
            r.recipientName = this.recipientName;
            r.recipientPhone = this.recipientPhone;
            r.dropoffAddress = this.dropoffAddress;
            r.dropoffCity = this.dropoffCity;
            r.serviceType = this.serviceType;
            r.weightKg = this.weightKg;
            return r;
        }
    }

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
}
