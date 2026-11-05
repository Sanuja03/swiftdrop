package com.swiftdrop.backend.dev;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Local development only: opens the dev tools and receipt downloads so the
 * customer pages can be tried before accounts exist. Never active outside the "dev" profile.
 */
@Profile(DevProfile.NAME)
@Configuration
public class DevSecurityConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain devSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(DevParcelStatusController.BASE_PATH + "/**", "/api/v1/receipts/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }
}
