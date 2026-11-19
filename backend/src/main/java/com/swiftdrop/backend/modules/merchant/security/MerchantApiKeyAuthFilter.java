package com.swiftdrop.backend.modules.merchant.security;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.backend.modules.merchant.entity.Merchant;
import com.swiftdrop.backend.modules.merchant.repository.MerchantRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authenticates merchant API calls by the X-API-KEY header. Runs only inside the merchant
 * security chain (/api/v1/merchant/**), never for other URLs.
 *
 * <p>No header: the request continues unauthenticated (or with its JWT) and the chain's rules
 * decide. Unknown key: 401. Key of a merchant that is not APPROVED: 403. The authenticated
 * principal carries the {@link #API_KEY_AUTHORITY} authority only.
 */
public class MerchantApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-KEY";
    public static final String API_KEY_AUTHORITY = "MERCHANT_API_KEY";

    private final MerchantRepository merchantRepository;
    private final com.swiftdrop.backend.modules.auth.repository.UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MerchantApiKeyAuthFilter(MerchantRepository merchantRepository,
            com.swiftdrop.backend.modules.auth.repository.UserRepository userRepository) {
        this.merchantRepository = merchantRepository;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        Merchant merchant = merchantRepository.findByApiKey(apiKey.strip()).orElse(null);
        if (merchant == null) {
            writeError(response, request, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", "Invalid API key.");
            return;
        }
        if (!merchant.isApproved()) {
            writeError(response, request, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                    "This merchant account is not approved yet.");
            return;
        }
        var user = userRepository.findById(merchant.getUserId()).orElse(null);
        if (user == null || user.getRole() != com.swiftdrop.backend.modules.auth.entity.Role.ROLE_MERCHANT
                || user.getStatus() != com.swiftdrop.backend.modules.auth.entity.AccountStatus.ACTIVE
                || !user.isEmailVerified()) {
            writeError(response, request, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                    "Email verification and an active merchant account are required.");
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                MerchantPrincipal.of(merchant), null, List.of(new SimpleGrantedAuthority(API_KEY_AUTHORITY)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    /** Same JSON shape as the auth module's entry point, so merchant clients see one error format for 401/403. */
    private void writeError(HttpServletResponse response, HttpServletRequest request, int status, String error,
                            String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status);
        body.put("error", error);
        body.put("message", message);
        body.put("path", request.getRequestURI());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
