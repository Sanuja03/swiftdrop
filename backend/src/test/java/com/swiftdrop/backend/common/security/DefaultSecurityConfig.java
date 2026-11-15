package com.swiftdrop.backend.common.security;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Test-only security for web slice tests that do not load the JWT SecurityConfig:
 * every non-public URL needs a login, as in the real app. Imported explicitly with
 * {@code @Import}; {@code @TestConfiguration} keeps it out of component scanning, so
 * full-application tests use only the real SecurityConfig.
 */
@TestConfiguration
public class DefaultSecurityConfig {

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/error").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(withDefaults())
                .build();
    }
}
