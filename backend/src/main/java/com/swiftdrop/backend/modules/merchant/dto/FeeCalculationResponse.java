package com.swiftdrop.backend.modules.merchant.dto;

import com.swiftdrop.backend.modules.parcel.entity.ServiceType;

import java.math.BigDecimal;

public class FeeCalculationResponse {
    private ServiceType serviceType;
    private BigDecimal weightKg;
    private BigDecimal baseFee;
    private BigDecimal weightCharge;
    private BigDecimal zoneSurcharge;
    private BigDecimal totalFee;
    private String currency;
    private String estimatedDeliveryTime;

    public FeeCalculationResponse() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ServiceType serviceType;
        private BigDecimal weightKg;
        private BigDecimal baseFee;
        private BigDecimal weightCharge;
        private BigDecimal zoneSurcharge;
        private BigDecimal totalFee;
        private String currency;
        private String estimatedDeliveryTime;

        public Builder serviceType(ServiceType serviceType) { this.serviceType = serviceType; return this; }
        public Builder weightKg(BigDecimal weightKg) { this.weightKg = weightKg; return this; }
        public Builder baseFee(BigDecimal baseFee) { this.baseFee = baseFee; return this; }
        public Builder weightCharge(BigDecimal weightCharge) { this.weightCharge = weightCharge; return this; }
        public Builder zoneSurcharge(BigDecimal zoneSurcharge) { this.zoneSurcharge = zoneSurcharge; return this; }
        public Builder totalFee(BigDecimal totalFee) { this.totalFee = totalFee; return this; }
        public Builder currency(String currency) { this.currency = currency; return this; }
        public Builder estimatedDeliveryTime(String estimatedDeliveryTime) { this.estimatedDeliveryTime = estimatedDeliveryTime; return this; }

        public FeeCalculationResponse build() {
            FeeCalculationResponse r = new FeeCalculationResponse();
            r.serviceType = this.serviceType;
            r.weightKg = this.weightKg;
            r.baseFee = this.baseFee;
            r.weightCharge = this.weightCharge;
            r.zoneSurcharge = this.zoneSurcharge;
            r.totalFee = this.totalFee;
            r.currency = this.currency;
            r.estimatedDeliveryTime = this.estimatedDeliveryTime;
            return r;
        }
    }

    public ServiceType getServiceType() { return serviceType; }
    public void setServiceType(ServiceType serviceType) { this.serviceType = serviceType; }

    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }

    public BigDecimal getBaseFee() { return baseFee; }
    public void setBaseFee(BigDecimal baseFee) { this.baseFee = baseFee; }

    public BigDecimal getWeightCharge() { return weightCharge; }
    public void setWeightCharge(BigDecimal weightCharge) { this.weightCharge = weightCharge; }

    public BigDecimal getZoneSurcharge() { return zoneSurcharge; }
    public void setZoneSurcharge(BigDecimal zoneSurcharge) { this.zoneSurcharge = zoneSurcharge; }

    public BigDecimal getTotalFee() { return totalFee; }
    public void setTotalFee(BigDecimal totalFee) { this.totalFee = totalFee; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getEstimatedDeliveryTime() { return estimatedDeliveryTime; }
    public void setEstimatedDeliveryTime(String estimatedDeliveryTime) { this.estimatedDeliveryTime = estimatedDeliveryTime; }
}
