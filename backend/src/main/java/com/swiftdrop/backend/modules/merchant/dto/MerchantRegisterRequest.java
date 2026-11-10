package com.swiftdrop.backend.modules.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public class MerchantRegisterRequest {

    private UUID userId;

    @NotBlank(message = "Business name is required")
    private String businessName;

    private String registrationNumber;

    @NotBlank(message = "Contact phone is required")
    private String contactPhone;

    @NotBlank(message = "Business address is required")
    private String businessAddress;

    private String email;

    public MerchantRegisterRequest() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID userId;
        private String businessName;
        private String registrationNumber;
        private String contactPhone;
        private String businessAddress;
        private String email;

        public Builder userId(UUID userId) { this.userId = userId; return this; }
        public Builder businessName(String businessName) { this.businessName = businessName; return this; }
        public Builder registrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; return this; }
        public Builder contactPhone(String contactPhone) { this.contactPhone = contactPhone; return this; }
        public Builder businessAddress(String businessAddress) { this.businessAddress = businessAddress; return this; }
        public Builder email(String email) { this.email = email; return this; }

        public MerchantRegisterRequest build() {
            MerchantRegisterRequest r = new MerchantRegisterRequest();
            r.userId = this.userId;
            r.businessName = this.businessName;
            r.registrationNumber = this.registrationNumber;
            r.contactPhone = this.contactPhone;
            r.businessAddress = this.businessAddress;
            r.email = this.email;
            return r;
        }
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }

    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getBusinessAddress() { return businessAddress; }
    public void setBusinessAddress(String businessAddress) { this.businessAddress = businessAddress; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
