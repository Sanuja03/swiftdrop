package com.swiftdrop.backend.modules.merchant.dto;

import jakarta.validation.constraints.NotBlank;

/** Merchant profile details. The account it belongs to is always the signed-in user. */
public class MerchantRegisterRequest {

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
        private String businessName;
        private String registrationNumber;
        private String contactPhone;
        private String businessAddress;
        private String email;

        public Builder businessName(String businessName) { this.businessName = businessName; return this; }
        public Builder registrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; return this; }
        public Builder contactPhone(String contactPhone) { this.contactPhone = contactPhone; return this; }
        public Builder businessAddress(String businessAddress) { this.businessAddress = businessAddress; return this; }
        public Builder email(String email) { this.email = email; return this; }

        public MerchantRegisterRequest build() {
            MerchantRegisterRequest r = new MerchantRegisterRequest();
            r.businessName = this.businessName;
            r.registrationNumber = this.registrationNumber;
            r.contactPhone = this.contactPhone;
            r.businessAddress = this.businessAddress;
            r.email = this.email;
            return r;
        }
    }

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
