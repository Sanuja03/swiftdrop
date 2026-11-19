package com.swiftdrop.backend.modules.merchant.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.backend.common.security.NotSignedInException;
import com.swiftdrop.backend.modules.auth.entity.User;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import com.swiftdrop.backend.modules.merchant.entity.Merchant;
import com.swiftdrop.backend.modules.merchant.exception.MerchantNotFoundException;
import com.swiftdrop.backend.modules.merchant.repository.MerchantRepository;

/**
 * The single place that decides which merchant a request acts for. It only ever uses the
 * authenticated principal: an API key (MerchantPrincipal) or a JWT (the user's email).
 * A merchant id sent by the client (query parameter, header, body) is never trusted.
 */
@Component
public class CurrentMerchantProvider {

    private final MerchantRepository merchantRepository;
    private final UserRepository userRepository;

    public CurrentMerchantProvider(MerchantRepository merchantRepository, UserRepository userRepository) {
        this.merchantRepository = merchantRepository;
        this.userRepository = userRepository;
    }

    /**
     * @throws NotSignedInException if nobody is signed in
     * @throws MerchantNotFoundException if the signed-in user has no merchant profile yet
     */
    @Transactional(readOnly = true)
    public Merchant currentMerchant() {
        Authentication authentication = authentication();
        if (authentication.getPrincipal() instanceof MerchantPrincipal principal) {
            return merchantRepository.findById(principal.merchantId())
                    .orElseThrow(() -> new MerchantNotFoundException(principal.merchantId()));
        }
        User user = currentUser();
        return merchantRepository.findByUserId(user.getId())
                .orElseThrow(() -> new MerchantNotFoundException("No merchant profile exists for this account yet."));
    }

    /**
     * The signed-in user (JWT). Used when registering a merchant profile, which is always linked
     * to the caller's own account.
     *
     * @throws NotSignedInException if nobody is signed in with a user account
     */
    @Transactional(readOnly = true)
    public User currentUser() {
        Authentication authentication = authentication();
        if (authentication.getPrincipal() instanceof MerchantPrincipal principal) {
            return userRepository.findById(principal.userId()).orElseThrow(NotSignedInException::new);
        }
        return userRepository.findByEmail(authentication.getName()).orElseThrow(NotSignedInException::new);
    }

    private static Authentication authentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new NotSignedInException();
        }
        return authentication;
    }
}
