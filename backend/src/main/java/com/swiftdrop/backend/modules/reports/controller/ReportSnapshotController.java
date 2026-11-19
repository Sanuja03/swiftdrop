package com.swiftdrop.backend.modules.reports.controller;

import com.swiftdrop.backend.modules.reports.dto.*;
import com.swiftdrop.backend.modules.reports.service.ReportSnapshotService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.UUID;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

@RestController
@RequestMapping("/api/v1/admin/reports/snapshots")
@PreAuthorize("hasRole('ADMIN')")
public class ReportSnapshotController {
    private final ReportSnapshotService snapshots;

    public ReportSnapshotController(ReportSnapshotService snapshots) {
        this.snapshots = snapshots;
    }

    @PostMapping
    public ReportSnapshotResponse save(@Valid @RequestBody SaveReportSnapshotRequest request, Principal principal) {
        return snapshots.save(request, principal.getName());
    }

    @GetMapping
    public ReportSnapshotPage list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return startDate == null && endDate == null ? snapshots.list(page, size)
                : snapshots.list(page, size, startDate, endDate);
    }

    @GetMapping("/{id}")
    public ReportSnapshotResponse get(@PathVariable UUID id) {
        return snapshots.get(id);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleStatus(ResponseStatusException error) {
        return ProblemDetail.forStatusAndDetail(error.getStatusCode(), error.getReason());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ProblemDetail handleInvalidRequest(Exception error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid saved-report request.");
    }
}
