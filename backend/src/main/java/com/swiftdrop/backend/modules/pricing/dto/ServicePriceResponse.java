package com.swiftdrop.backend.modules.pricing.dto;

import java.math.BigDecimal;

import com.swiftdrop.backend.modules.parcel.entity.ServiceType;

/**
 * Starting price of a service for the public "From LKR ..." labels: the cheapest possible
 * booking (up to the included weight, within one zone, in the zone with the lowest surcharge).
 */
public record ServicePriceResponse(ServiceType serviceType, BigDecimal fromPrice, String currency) { }
