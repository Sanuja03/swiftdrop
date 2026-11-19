package com.swiftdrop.backend.modules.merchant.security;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.swiftdrop.backend.modules.auth.security.CustomAccessDeniedHandler;
import com.swiftdrop.backend.modules.auth.security.CustomAuthenticationEntryPoint;
import com.swiftdrop.backend.modules.auth.security.JwtAuthenticationFilter;
import com.swiftdrop.backend.modules.merchant.repository.MerchantRepository;

/**
 * Security for {@code /api/v1/merchant/**} only. Every other URL is left to the main JWT
 * SecurityConfig (this chain has a securityMatcher and is ordered before it).
 *
 * <ul>
 *   <li>{@code /api/v1/merchant/api/v1/**}: the machine API, X-API-KEY only.</li>
 *   <li>Everything else under {@code /api/v1/merchant/**} (portal: profile, orders dashboard,
 *       webhook settings, webhook delivery log): a JWT for a ROLE_MERCHANT user.</li>
 * </ul>
 * An API key grants the MERCHANT_API_KEY authority only, never ROLE_MERCHANT, so it cannot
 * open the portal endpoints, and it is ignored on every path outside this chain.
 * Merchant approval and other admin endpoints live under /api/v1/admin/** (main chain, ADMIN).
 */
@Configuration
public class MerchantSecurityConfig {

    public static final String BASE_PATH = "/api/v1/merchant";
    public static final String API_PATH = BASE_PATH + "/api/v1";

    @Bean
    @Order(3)
    public SecurityFilterChain merchantSecurityFilterChain(HttpSecurity http,
                                                           MerchantRepository merchantRepository,
                                                           JwtAuthenticationFilter jwtAuthenticationFilter,
                                                           CustomAuthenticationEntryPoint authenticationEntryPoint,
                                                           CustomAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                .securityMatcher(BASE_PATH + "/**")
                // Same allowed origins as the main chain (CORS_ALLOWED_ORIGINS via the app's CorsConfigurationSource)
                .cors(withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(API_PATH + "/**").hasAuthority(MerchantApiKeyAuthFilter.API_KEY_AUTHORITY)
                        .anyRequest().hasRole("MERCHANT"))
                // Not a Spring bean on purpose: as a bean, Spring Boot would also run it for every URL.
                .addFilterBefore(new MerchantApiKeyAuthFilter(merchantRepository), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
