package com.swiftdrop.backend.modules.webhook.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.swiftdrop.backend.modules.messaging.config.MessagingProperties;

/**
 * POSTs a webhook body to a merchant endpoint. Never throws: every problem comes back
 * as a failed {@link WebhookHttpResult} so the caller can schedule a retry. The response
 * body is discarded, so nothing the merchant returns ends up in our logs or database.
 */
@Component
public class WebhookHttpClient {

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public WebhookHttpClient(MessagingProperties properties) {
        this.requestTimeout = properties.webhook().requestTimeout();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.webhook().connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public WebhookHttpResult post(String url, String body, Map<String, String> headers) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException ex) {
            return WebhookHttpResult.noResponse("Webhook URL is not valid");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
            return WebhookHttpResult.noResponse("Webhook URL must use http or https");
        }

        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        headers.forEach(request::header);

        try {
            HttpResponse<Void> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.discarding());
            return WebhookHttpResult.response(response.statusCode());
        } catch (HttpTimeoutException ex) {
            return WebhookHttpResult.noResponse("Endpoint did not answer within " + requestTimeout.toSeconds() + "s");
        } catch (IOException ex) {
            return WebhookHttpResult.noResponse("Could not reach endpoint (" + ex.getClass().getSimpleName() + ")");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return WebhookHttpResult.noResponse("Delivery was interrupted");
        }
    }
}
