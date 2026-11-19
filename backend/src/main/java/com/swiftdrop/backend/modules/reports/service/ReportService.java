
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
                COUNT(*) FILTER (WHERE status = 'FAILED') AS failed_parcels,
                COALESCE(SUM(fee), 0) AS total_fees
            FROM public.parcels
            """;

        return jdbcTemplate.queryForObject(sql, (rs, rowNum) ->
            new ReportSummaryResponse(
                rs.getLong("total_parcels"),
                rs.getLong("delivered_parcels"),
                rs.getLong("in_transit_parcels"),
                rs.getLong("out_for_delivery_parcels"),
                rs.getBigDecimal("total_fees"),
                rs.getLong("failed_parcels")
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
        return querySuccessRate(null, null);
    }

    public DeliverySuccessRateResponse getDeliverySuccessRate(LocalDate startDate, LocalDate endDate) {
        return querySuccessRate(startDate, endDate);
    }

    private DeliverySuccessRateResponse querySuccessRate(LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT
                COUNT(*) FILTER (
                    WHERE status = 'DELIVERED'
                ) AS delivered,
                COUNT(*) FILTER (
                    WHERE status = 'FAILED'
                ) AS failed
            FROM public.parcels p
            """;

        // Current completed outcomes, dated by the latest delivered/failed status event.
        // Aggregate history first so retries never duplicate a parcel in the percentages.
        if (startDate != null && endDate != null) {
            sql += """
                JOIN (
                    SELECT parcel_id, MAX(changed_at) AS outcome_at
                    FROM public.parcel_status_history
                    WHERE to_status IN ('DELIVERED', 'FAILED')
                    GROUP BY parcel_id
                ) h ON h.parcel_id = p.id
                WHERE (h.outcome_at AT TIME ZONE 'Asia/Colombo')::date BETWEEN ? AND ?
                """;
        }

        org.springframework.jdbc.core.RowMapper<DeliverySuccessRateResponse> rowMapper = (rs, rowNum) -> {

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
        };
        return startDate == null ? jdbcTemplate.queryForObject(sql, rowMapper)
                : jdbcTemplate.queryForObject(sql, rowMapper, startDate, endDate);
    }
}
