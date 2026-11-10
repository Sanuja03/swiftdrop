package com.swiftdrop.backend.modules.merchant.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.backend.modules.merchant.entity.Merchant;
import com.swiftdrop.backend.modules.merchant.repository.MerchantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class MerchantApiKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(MerchantApiKeyAuthFilter.class);

    public static final String API_KEY_HEADER = "X-API-KEY";
    public static final String AUTHENTICATED_MERCHANT_ATTR = "authenticatedMerchant";

    private final MerchantRepository merchantRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MerchantApiKeyAuthFilter(MerchantRepository merchantRepository) {
        this.merchantRepository = merchantRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey == null || apiKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<Merchant> merchantOpt = merchantRepository.findByApiKey(apiKey.trim());

        if (merchantOpt.isEmpty()) {
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid API Key provided.");
            return;
        }

        Merchant merchant = merchantOpt.get();

        if (!merchant.isApproved()) {
            writeErrorResponse(response, HttpServletResponse.SC_FORBIDDEN,
                    "Merchant account is currently " + merchant.getRegistrationStatus() + ". An approved account is required.");
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                merchant.getId().toString(),
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_MERCHANT"))
        );
        authentication.setDetails(merchant);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        request.setAttribute(AUTHENTICATED_MERCHANT_ATTR, merchant);
        log.debug("Authenticated merchant '{}' via API key", merchant.getBusinessName());

        filterChain.doFilter(request, response);
    }

    private void writeErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");

        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("timestamp", Instant.now().toString());
        errorDetails.put("status", status);
        errorDetails.put("error", status == 401 ? "Unauthorized" : "Forbidden");
        errorDetails.put("message", message);

        response.getWriter().write(objectMapper.writeValueAsString(errorDetails));
    }
}
