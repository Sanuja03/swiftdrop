package com.swiftdrop.backend.modules.booking.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.swiftdrop.backend.modules.booking.exception.BookingException;

class CurrentCustomerProviderTest {
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final CurrentCustomerProvider provider = new CurrentCustomerProvider(jdbcTemplate);

    @Test
    void returnsDevelopmentCustomer() {
        var customer = new BookingCustomer(UUID.fromString("50000000-0000-4000-8000-000000000001"), "Test Customer");
        when(jdbcTemplate.query(eq(CurrentCustomerProvider.DEV_CUSTOMER_SQL), any(RowMapper.class)))
                .thenReturn(List.of(customer));

        assertThat(provider.currentCustomer()).isEqualTo(customer);
    }

    @Test
    void noCustomerIsReportedAsUnavailable() {
        when(jdbcTemplate.query(eq(CurrentCustomerProvider.DEV_CUSTOMER_SQL), any(RowMapper.class)))
                .thenReturn(List.of());

        assertThatThrownBy(provider::currentCustomer).isInstanceOf(BookingException.class);
    }

    @Test
    void missingUsersTableIsReportedAsUnavailable() {
        when(jdbcTemplate.query(eq(CurrentCustomerProvider.DEV_CUSTOMER_SQL), any(RowMapper.class)))
                .thenThrow(new BadSqlGrammarException("dev customer", CurrentCustomerProvider.DEV_CUSTOMER_SQL,
                        new java.sql.SQLException("relation \"users\" does not exist")));

        assertThatThrownBy(provider::currentCustomer).isInstanceOf(BookingException.class);
    }
}
