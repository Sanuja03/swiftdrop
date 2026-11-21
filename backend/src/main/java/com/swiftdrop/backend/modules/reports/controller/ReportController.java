
package com.swiftdrop.backend.modules.reports.controller;

import com.swiftdrop.backend.modules.reports.service.ReportService;
import com.swiftdrop.backend.modules.reports.dto.ReportSummaryResponse;
import com.swiftdrop.backend.modules.reports.dto.DailyDeliveryResponse;
import com.swiftdrop.backend.modules.reports.dto.DeliverySuccessRateResponse;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/test")
    public String testReport() {
        return "SwiftDrop Reports API is working!";
    }

    @GetMapping("/total-parcels")
    public Long getTotalParcels() {
        return reportService.getTotalParcels();
    }

    @GetMapping("/summary")
    public ReportSummaryResponse getReportSummary() {
        return reportService.getReportSummary();
    }

    @GetMapping("/daily-deliveries")
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

    @GetMapping("/success-rate")
    public DeliverySuccessRateResponse getDeliverySuccessRate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (startDate == null && endDate == null) return reportService.getDeliverySuccessRate();
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide a valid startDate and endDate.");
        }
        return reportService.getDeliverySuccessRate(startDate, endDate);
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleStatus(ResponseStatusException exception) {
        return ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleInvalidDate(MethodArgumentTypeMismatchException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Report dates must use YYYY-MM-DD format.");
    }

}
