package com.swiftdrop.backend.modules.merchant.service;

import com.swiftdrop.backend.modules.merchant.dto.FeeCalculationRequest;
import com.swiftdrop.backend.modules.merchant.dto.FeeCalculationResponse;
import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MerchantPricingServiceTest {

    private MerchantPricingService pricingService;

    @BeforeEach
    void setUp() {
        pricingService = new MerchantPricingService();
    }

    @Test
    void testStandardDeliveryBaseRate() {
        FeeCalculationRequest request = FeeCalculationRequest.builder()
                .serviceType(ServiceType.STANDARD)
                .weightKg(new BigDecimal("1.0"))
                .pickupCity("Colombo")
                .dropoffCity("Colombo")
                .build();

        FeeCalculationResponse response = pricingService.calculateFee(request);

        assertNotNull(response);
        assertEquals(new BigDecimal("500.00"), response.getBaseFee());
        assertEquals(BigDecimal.ZERO, response.getWeightCharge());
        assertEquals(BigDecimal.ZERO, response.getZoneSurcharge());
        assertEquals(new BigDecimal("500.00"), response.getTotalFee());
        assertEquals("LKR", response.getCurrency());
    }

    @Test
    void testStandardDeliveryWithWeightAndInterCitySurcharge() {
        FeeCalculationRequest request = FeeCalculationRequest.builder()
                .serviceType(ServiceType.STANDARD)
                .weightKg(new BigDecimal("5.0")) // 4 kg extra * 18 = 72 + 500 base + 90 zone = 662
                .pickupCity("Colombo")
                .dropoffCity("Kandy")
                .build();

        FeeCalculationResponse response = pricingService.calculateFee(request);

        assertEquals(new BigDecimal("500.00"), response.getBaseFee());
        assertEquals(new BigDecimal("72.00"), response.getWeightCharge());
        assertEquals(new BigDecimal("90.00"), response.getZoneSurcharge());
        assertEquals(new BigDecimal("662.00"), response.getTotalFee());
    }

    @Test
    void testExpressDeliveryCalculation() {
        FeeCalculationRequest request = FeeCalculationRequest.builder()
                .serviceType(ServiceType.EXPRESS)
                .weightKg(new BigDecimal("2.5")) // 1.5 kg extra * 25 = 37.50 + 900 = 937.50
                .pickupCity("Colombo")
                .dropoffCity("Colombo")
                .build();

        FeeCalculationResponse response = pricingService.calculateFee(request);

        assertEquals(new BigDecimal("900.00"), response.getBaseFee());
        assertEquals(new BigDecimal("37.50"), response.getWeightCharge());
        assertEquals(new BigDecimal("937.50"), response.getTotalFee());
    }

    @Test
    void testSameDayDeliveryCalculation() {
        FeeCalculationRequest request = FeeCalculationRequest.builder()
                .serviceType(ServiceType.SAME_DAY)
                .weightKg(new BigDecimal("1.0"))
                .pickupCity("Colombo")
                .dropoffCity("Colombo")
                .build();

        FeeCalculationResponse response = pricingService.calculateFee(request);

        assertEquals(new BigDecimal("1400.00"), response.getBaseFee());
        assertEquals(new BigDecimal("1400.00"), response.getTotalFee());
    }
}

