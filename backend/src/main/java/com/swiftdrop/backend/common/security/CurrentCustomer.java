package com.swiftdrop.backend.common.security;

import java.util.UUID;

/** The signed-in user acting as a customer: their users.id and the name printed as sender. */
public record CurrentCustomer(UUID id, String fullName) { }
