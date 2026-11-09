
package com.swiftdrop.backend.modules.reports.controller;

import com.swiftdrop.backend.modules.reports.service.ReportService;
import com.swiftdrop.backend.modules.reports.dto.ReportSummaryResponse;
import com.swiftdrop.backend.modules.reports.dto.DailyDeliveryResponse;
import com.swiftdrop.backend.modules.reports.dto.DeliverySuccessRateResponse;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@RestController
@PreAuthorize("hasRole('ADMIN')")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/api/reports/test")
    public String testReport() {
        return "SwiftDrop Reports API is working!";
    }

    @GetMapping("/api/reports/total-parcels")
    public Long getTotalParcels() {
        return reportService.getTotalParcels();
    }

    @GetMapping("/api/reports/summary")
    public ReportSummaryResponse getReportSummary() {
        return reportService.getReportSummary();
    }

    @GetMapping("/api/reports/daily-deliveries")
    public List<DailyDeliveryResponse> getDailyDeliveries(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate
    ) {
        // Default to the last 7 days in Sri Lankan time.
        LocalDate today = LocalDate.now(
            ZoneId.of("Asia/Colombo")
        );

        if (startDate == null) {
            startDate = today.minusDays(6);
        }

        if (endDate == null) {
            endDate = today;
        }

        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "startDate must not be after endDate"
            );
        }

        return reportService.getDailyDeliveries(
            startDate,
            endDate
        );
    }

    @GetMapping("/api/reports/success-rate")
    public DeliverySuccessRateResponse getDeliverySuccessRate() {
        return reportService.getDeliverySuccessRate();
    }
}
