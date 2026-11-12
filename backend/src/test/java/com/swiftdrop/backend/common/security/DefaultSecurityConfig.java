package com.swiftdrop.backend.common.security;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Keeps every non-public URL locked, as Spring Boot's own default would.
 *
 * <p>Spring Boot drops its default chain as soon as any SecurityFilterChain bean
 * exists, so this restores it. Delete this class when the JWT SecurityConfig from
 * the auth module is merged, since that chain replaces it.
 */
@Configuration
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
