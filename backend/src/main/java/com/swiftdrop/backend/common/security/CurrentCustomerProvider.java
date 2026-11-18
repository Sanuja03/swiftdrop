package com.swiftdrop.backend.common.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.backend.modules.auth.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * The single place that works out who the signed-in customer is. It reads the user
 * from the JWT that JwtAuthenticationFilter has put in the SecurityContext (the
 * principal name is the user's email) and loads that user.
 *
 * <p>Endpoints that return "my" data must use this and never trust a customer id
 * sent by the client.
 */
@Component
@RequiredArgsConstructor
public class CurrentCustomerProvider {

    private final UserRepository userRepository;

    /**
     * @throws NotSignedInException if nobody is signed in or the user no longer exists
     */
    @Transactional(readOnly = true)
    public CurrentCustomer currentCustomer() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new NotSignedInException();
        }
        return userRepository.findByEmail(authentication.getName())
                .map(user -> new CurrentCustomer(user.getId(), user.getFullName()))
                .orElseThrow(NotSignedInException::new);
    }
}
