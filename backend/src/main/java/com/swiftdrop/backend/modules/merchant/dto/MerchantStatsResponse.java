package com.swiftdrop.backend.modules.merchant.dto;

public class MerchantStatsResponse {
    private long totalOrders;
    private long activeShipments;
    private long deliveredOrders;
    private long pendingOrders;
    private long cancelledOrders;

    public MerchantStatsResponse() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private long totalOrders;
        private long activeShipments;
        private long deliveredOrders;
        private long pendingOrders;
        private long cancelledOrders;

        public Builder totalOrders(long totalOrders) { this.totalOrders = totalOrders; return this; }
        public Builder activeShipments(long activeShipments) { this.activeShipments = activeShipments; return this; }
        public Builder deliveredOrders(long deliveredOrders) { this.deliveredOrders = deliveredOrders; return this; }
        public Builder pendingOrders(long pendingOrders) { this.pendingOrders = pendingOrders; return this; }
        public Builder cancelledOrders(long cancelledOrders) { this.cancelledOrders = cancelledOrders; return this; }

        public MerchantStatsResponse build() {
            MerchantStatsResponse r = new MerchantStatsResponse();
            r.totalOrders = this.totalOrders;
            r.activeShipments = this.activeShipments;
            r.deliveredOrders = this.deliveredOrders;
            r.pendingOrders = this.pendingOrders;
            r.cancelledOrders = this.cancelledOrders;
            return r;
        }
    }

    public long getTotalOrders() { return totalOrders; }
    public void setTotalOrders(long totalOrders) { this.totalOrders = totalOrders; }

    public long getActiveShipments() { return activeShipments; }
    public void setActiveShipments(long activeShipments) { this.activeShipments = activeShipments; }

    public long getDeliveredOrders() { return deliveredOrders; }
    public void setDeliveredOrders(long deliveredOrders) { this.deliveredOrders = deliveredOrders; }

    public long getPendingOrders() { return pendingOrders; }
    public void setPendingOrders(long pendingOrders) { this.pendingOrders = pendingOrders; }

    public long getCancelledOrders() { return cancelledOrders; }
    public void setCancelledOrders(long cancelledOrders) { this.cancelledOrders = cancelledOrders; }
}
