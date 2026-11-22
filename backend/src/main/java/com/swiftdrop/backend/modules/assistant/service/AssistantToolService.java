package com.swiftdrop.backend.modules.assistant.service;

import com.swiftdrop.backend.modules.assistant.dto.CustomerParcelSummary;
import com.swiftdrop.backend.modules.assistant.dto.EstimateFeeToolRequest;
import com.swiftdrop.backend.modules.assistant.dto.EstimateFeeToolResult;
import com.swiftdrop.backend.modules.assistant.dto.TrackParcelResult;

import java.util.List;
import java.util.UUID;

/**
 * Reusable backend tool service for the SwiftDrop AI Delivery Assistant and MCP integration.
 * Exposes operations for tracking parcels, calculating delivery fees, and listing customer parcels.
 */
public interface AssistantToolService {

    /**
     * Tool: track_parcel
     * Retrieves parcel tracking details by tracking number.
     *
     * @param trackingNumber the parcel tracking number
     * @return structured tracking result (found or not found)
     */
    TrackParcelResult trackParcel(String trackingNumber);

    /**
     * Tool: estimate_fee
     * Calculates parcel delivery fee using existing pricing rules and active zones.
     *
     * @param request fee estimation parameters (cities, weight, service type)
     * @return structured fee estimate result or validation details
     */
    EstimateFeeToolResult estimateFee(EstimateFeeToolRequest request);

    /**
     * Tool: list_my_parcels
     * Retrieves parcels belonging to the currently authenticated customer from the security context.
     *
     * @return list of parcel summaries for the authenticated customer
     */
    List<CustomerParcelSummary> listMyParcels();

    /**
     * Tool: list_my_parcels (explicit authenticated customer ID overload).
     *
     * @param customerId verified authenticated customer ID (from SecurityContext or session)
     * @return list of parcel summaries
     */
    List<CustomerParcelSummary> listMyParcels(UUID customerId);
}
