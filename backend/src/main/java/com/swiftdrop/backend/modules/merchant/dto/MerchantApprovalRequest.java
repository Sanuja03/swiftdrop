package com.swiftdrop.backend.modules.merchant.dto;

import java.util.UUID;

public class MerchantApprovalRequest {
    private UUID adminId;
    private String notes;

    public MerchantApprovalRequest() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID adminId;
        private String notes;

        public Builder adminId(UUID adminId) { this.adminId = adminId; return this; }
        public Builder notes(String notes) { this.notes = notes; return this; }

        public MerchantApprovalRequest build() {
            MerchantApprovalRequest r = new MerchantApprovalRequest();
            r.adminId = this.adminId;
            r.notes = this.notes;
            return r;
        }
    }

    public UUID getAdminId() { return adminId; }
    public void setAdminId(UUID adminId) { this.adminId = adminId; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
