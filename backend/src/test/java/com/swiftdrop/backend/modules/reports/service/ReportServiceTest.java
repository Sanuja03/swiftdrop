package com.swiftdrop.backend.modules.reports.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ReportServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ReportService reports = new ReportService(jdbc);

    private void outcomes(long delivered, long failed) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getLong("delivered")).thenReturn(delivered);
        when(row.getLong("failed")).thenReturn(failed);
        when(jdbc.queryForObject(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            return mapper.mapRow(row, 0);
        });
    }

    @Test
    void emptyOutcomesProduceZeroRates() throws Exception {
        outcomes(0, 0);
        var result = reports.getDeliverySuccessRate();
        assertEquals(0.0, result.successRate());
        assertEquals(0.0, result.failureRate());
    }

    @Test
    void ratesUseDeliveredAndFailedCounts() throws Exception {
        outcomes(3, 1);
        var result = reports.getDeliverySuccessRate();
        assertEquals(3L, result.deliveredParcels());
        assertEquals(1L, result.failedParcels());
        assertEquals(75.0, result.successRate());
        assertEquals(25.0, result.failureRate());
    }
}
