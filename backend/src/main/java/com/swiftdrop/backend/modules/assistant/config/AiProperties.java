package com.swiftdrop.backend.modules.assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    /**
     * Base URL of the OpenAI-compatible AI provider.
     * Default: https://api.openai.com/v1 (or Google Gemini: https://generativelanguage.googleapis.com/v1beta/openai)
     */
    private String baseUrl = "https://api.openai.com/v1";

    /**
     * Provider API key injected via environment variable (AI_API_KEY, OPENAI_API_KEY, or GEMINI_API_KEY).
     */
    private String apiKey;

    /**
     * Model identifier, e.g. gpt-4o-mini (default) or gemini-3.8-flash.
     */
    private String model = "gpt-4o-mini";

    /**
     * Maximum number of tool-calling iterations allowed in a single conversation turn.
     */
    private int maxIterations = 5;

    /**
     * HTTP connection and read timeout in seconds.
     */
    private int timeoutSeconds = 30;
}
