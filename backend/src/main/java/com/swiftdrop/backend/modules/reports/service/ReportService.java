
package com.swiftdrop.backend.modules.reports.service;

import com.swiftdrop.backend.modules.reports.dto.ReportSummaryResponse;
import com.swiftdrop.backend.modules.reports.dto.DailyDeliveryResponse;
import com.swiftdrop.backend.modules.reports.dto.DeliverySuccessRateResponse;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class ReportService {

    private final JdbcTemplate jdbcTemplate;

    public ReportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Get total number of parcels
    public Long getTotalParcels() {
        String sql = "SELECT COUNT(*) FROM public.parcels";

        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    // Get overall parcel summary
    public ReportSummaryResponse getReportSummary() {
        String sql = """
            SELECT
                COUNT(*) AS total_parcels,
                COUNT(*) FILTER (
                    WHERE status = 'DELIVERED'
                ) AS delivered_parcels,
                COUNT(*) FILTER (
                    WHERE status = 'IN_TRANSIT'
                ) AS in_transit_parcels,
                COUNT(*) FILTER (
                    WHERE status = 'OUT_FOR_DELIVERY'
                ) AS out_for_delivery_parcels,
                COALESCE(SUM(fee), 0) AS total_fees
            FROM public.parcels
            """;

        return jdbcTemplate.queryForObject(sql, (rs, rowNum) ->
            new ReportSummaryResponse(
                rs.getLong("total_parcels"),
                rs.getLong("delivered_parcels"),
                rs.getLong("in_transit_parcels"),
                rs.getLong("out_for_delivery_parcels"),
                rs.getBigDecimal("total_fees")
            )
        );
    }

    // Get completed deliveries within a selected date range
    public List<DailyDeliveryResponse> getDailyDeliveries(
            LocalDate startDate,
            LocalDate endDate
    ) {
        String sql = """
            SELECT
                (changed_at AT TIME ZONE 'Asia/Colombo')::date
                    AS delivery_date,
                COUNT(DISTINCT parcel_id) AS deliveries
            FROM public.parcel_status_history
            WHERE to_status = 'DELIVERED'
              AND (changed_at AT TIME ZONE 'Asia/Colombo')::date
                  BETWEEN ? AND ?
            GROUP BY delivery_date
            ORDER BY delivery_date
            """;

        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new DailyDeliveryResponse(
                rs.getObject("delivery_date", LocalDate.class),
                rs.getLong("deliveries")
            ),
            startDate,
            endDate
        );
    }

    // Get delivery success and failure rates
    public DeliverySuccessRateResponse getDeliverySuccessRate() {
        String sql = """
            SELECT
                COUNT(*) FILTER (
                    WHERE status = 'DELIVERED'
                ) AS delivered,
                COUNT(*) FILTER (
                    WHERE status = 'FAILED'
                ) AS failed
            FROM public.parcels
            """;

        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {

            long delivered = rs.getLong("delivered");
            long failed = rs.getLong("failed");

            long total = delivered + failed;

            double successRate = total == 0
                    ? 0.0
                    : (delivered * 100.0) / total;

            double failureRate = total == 0
                    ? 0.0
                    : (failed * 100.0) / total;

            return new DeliverySuccessRateResponse(
                delivered,
                failed,
                successRate,
                failureRate
            );
        });
    }
}
