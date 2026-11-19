package com.swiftdrop.backend.modules.merchant.dto;

import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class FeeCalculationRequest {

    @NotNull(message = "Service type is required")
    private ServiceType serviceType;

    @NotNull(message = "Weight in kg is required")
    @DecimalMin(value = "0.1", message = "Weight must be at least 0.1 kg")
    private BigDecimal weightKg;

    private String pickupCity;
    private String dropoffCity;

    public FeeCalculationRequest() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ServiceType serviceType;
        private BigDecimal weightKg;
        private String pickupCity;
        private String dropoffCity;

        public Builder serviceType(ServiceType serviceType) { this.serviceType = serviceType; return this; }
        public Builder weightKg(BigDecimal weightKg) { this.weightKg = weightKg; return this; }
        public Builder pickupCity(String pickupCity) { this.pickupCity = pickupCity; return this; }
        public Builder dropoffCity(String dropoffCity) { this.dropoffCity = dropoffCity; return this; }

        public FeeCalculationRequest build() {
            FeeCalculationRequest r = new FeeCalculationRequest();
            r.serviceType = this.serviceType;
            r.weightKg = this.weightKg;
            r.pickupCity = this.pickupCity;
            r.dropoffCity = this.dropoffCity;
            return r;
        }
    }

    public ServiceType getServiceType() { return serviceType; }
    public void setServiceType(ServiceType serviceType) { this.serviceType = serviceType; }

    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }

    public String getPickupCity() { return pickupCity; }
    public void setPickupCity(String pickupCity) { this.pickupCity = pickupCity; }

    public String getDropoffCity() { return dropoffCity; }
    public void setDropoffCity(String dropoffCity) { this.dropoffCity = dropoffCity; }
}
