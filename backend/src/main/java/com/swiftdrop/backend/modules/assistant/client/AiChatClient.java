package com.swiftdrop.backend.modules.assistant.client;

import com.swiftdrop.backend.modules.assistant.client.dto.ChatCompletionRequest;
import com.swiftdrop.backend.modules.assistant.client.dto.ChatCompletionResponse;
import com.swiftdrop.backend.modules.assistant.config.AiProperties;
import com.swiftdrop.backend.modules.assistant.exception.AiConfigurationException;
import com.swiftdrop.backend.modules.assistant.exception.AiServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;

/**
 * Lightweight, production-grade HTTP client communicating with an OpenAI-compatible
 * or Google Gemini Generative AI provider using Spring Framework's RestClient.
 */
@Slf4j
@Component
public class AiChatClient {

    private final RestClient restClient;
    private final AiProperties aiProperties;

    public AiChatClient(AiProperties aiProperties) {
        this.aiProperties = aiProperties;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(aiProperties.getTimeoutSeconds()));
        requestFactory.setReadTimeout(Duration.ofSeconds(aiProperties.getTimeoutSeconds()));

        this.restClient = RestClient.builder()
                .baseUrl(aiProperties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public ChatCompletionResponse sendChatCompletion(ChatCompletionRequest request) {
        if (!StringUtils.hasText(aiProperties.getApiKey())) {
            throw new AiConfigurationException(
                    "Generative AI API key is not configured. Please set the AI_API_KEY environment variable."
            );
        }

        try {
            log.debug("Sending chat completion request to model [{}]", request.getModel());
            return restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + aiProperties.getApiKey().trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatCompletionResponse.class);
        } catch (RestClientResponseException ex) {
            log.error("AI provider returned error status [{}]: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new AiServiceException(
                    "AI provider returned error code: " + ex.getStatusCode().value(),
                    ex
            );
        } catch (ResourceAccessException ex) {
            log.error("Failed to connect to AI provider: {}", ex.getMessage());
            throw new AiServiceException(
                    "Failed to connect to AI provider. Connection timed out or unreachable.",
                    ex
            );
        } catch (Exception ex) {
            log.error("Unexpected error invoking Generative AI provider", ex);
            throw new AiServiceException(
                    "Unexpected error during AI generation: " + ex.getMessage(),
                    ex
            );
        }
    }
}
