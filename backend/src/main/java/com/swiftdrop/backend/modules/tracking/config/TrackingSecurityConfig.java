package com.swiftdrop.backend.modules.tracking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import com.swiftdrop.backend.modules.tracking.controller.PublicTrackingController;
import com.swiftdrop.backend.modules.tracking.websocket.WebSocketConfig;

/**
 * Opens only the public tracking API and the live-tracking WebSocket.
 * Matched before the application's main security chain; every other URL is left to that chain.
 */
@Configuration
public class TrackingSecurityConfig {

    static final String[] PUBLIC_PATHS = {
            PublicTrackingController.BASE_PATH + "/**",
            WebSocketConfig.ENDPOINT + "/**"
    };

    @Bean
    @Order(1)
    public SecurityFilterChain publicTrackingSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(PUBLIC_PATHS)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }
}
