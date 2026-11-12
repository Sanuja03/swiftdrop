package com.swiftdrop.backend.modules.booking.customer;

import java.util.UUID;

/** The customer placing a booking: their users.id and the name printed as sender. */
public record BookingCustomer(UUID id, String fullName) { }
