package com.swiftdrop.backend.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a centrally managed {@link ObjectMapper} bean configured with
 * auto-discovered modules (e.g. JavaTimeModule for java.time types).
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
