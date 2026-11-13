package com.swiftdrop.backend.modules.booking.customer;

import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.swiftdrop.backend.modules.booking.exception.BookingException;

import lombok.RequiredArgsConstructor;

/**
 * Works out which customer is placing a booking. This is the ONLY place booking
 * decides who the customer is.
 *
 * <p>TODO(auth): replace with the authenticated user from the JWT once the auth
 * module (feature/auth-and-accounts) is merged, e.g. read the user id from the
 * SecurityContext and load that user. Until then it uses a development user:
 * the oldest ACTIVE user with role ROLE_CUSTOMER in the users table. Plain SQL is
 * used because the User entity is not on this branch yet.
 */
@Component
@RequiredArgsConstructor
public class CurrentCustomerProvider {

    static final String DEV_CUSTOMER_SQL = """
            SELECT id, full_name FROM users
            WHERE role = 'ROLE_CUSTOMER' AND status = 'ACTIVE'
            ORDER BY created_at, id
            LIMIT 1
            """;

    private final JdbcTemplate jdbcTemplate;

    /**
     * @throws BookingException if no customer can be found
     */
    public BookingCustomer currentCustomer() {
        try {
            return jdbcTemplate.query(DEV_CUSTOMER_SQL,
                            (rs, row) -> new BookingCustomer(rs.getObject("id", UUID.class), rs.getString("full_name")))
                    .stream()
                    .findFirst()
                    .orElseThrow(BookingException::customerUnavailable);
        } catch (DataAccessException ex) {
            // For example the users table does not exist yet in this database.
            throw BookingException.customerUnavailable();
        }
    }
}
