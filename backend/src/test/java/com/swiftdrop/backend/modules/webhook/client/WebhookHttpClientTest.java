package com.swiftdrop.backend.modules.webhook.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;
import com.swiftdrop.backend.modules.messaging.config.MessagingProperties;

/**
 * Talks to a real local HTTP server, so headers, status codes and timeouts are tested for real.
 */
class WebhookHttpClientTest {

    private HttpServer server;
    private final AtomicInteger responseStatus = new AtomicInteger(200);
    private final AtomicInteger responseDelayMs = new AtomicInteger(0);
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicReference<String> receivedSignature = new AtomicReference<>();

    private WebhookHttpClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            receivedSignature.set(exchange.getRequestHeaders().getFirst("X-SwiftDrop-Signature"));
            sleep(responseDelayMs.get());
            exchange.sendResponseHeaders(responseStatus.get(), -1);
            exchange.close();
        });
        server.start();

        client = new WebhookHttpClient(new MessagingProperties(
                new MessagingProperties.Outbox(true, 50, Duration.ofSeconds(5), Duration.ofMinutes(5)),
                new MessagingProperties.Webhook(List.of(Duration.ofSeconds(10)), Duration.ofSeconds(1),
                        Duration.ofMillis(500))));
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void postsBodyAndHeadersAndReportsSuccess() {
        WebhookHttpResult result = client.post(url(), "{\"a\":1}", Map.of("X-SwiftDrop-Signature", "t=1,v1=ab"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.error()).isNull();
        assertThat(receivedBody.get()).isEqualTo("{\"a\":1}");
        assertThat(receivedSignature.get()).isEqualTo("t=1,v1=ab");
    }

    @Test
    void serverErrorIsAFailureWithItsStatusCode() {
        responseStatus.set(500);

        WebhookHttpResult result = client.post(url(), "{}", Map.of());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.statusCode()).isEqualTo(500);
        assertThat(result.error()).isEqualTo("Endpoint answered HTTP 500");
    }

    @Test
    void redirectIsNotFollowed() {
        responseStatus.set(302);

        assertThat(client.post(url(), "{}", Map.of()).isSuccess()).isFalse();
    }

    @Test
    void slowEndpointTimesOut() {
        responseDelayMs.set(1500);

        WebhookHttpResult result = client.post(url(), "{}", Map.of());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.statusCode()).isNull();
        assertThat(result.error()).contains("did not answer");
    }

    @Test
    void unreachableEndpointIsAFailureNotAnException() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        WebhookHttpResult result = client.post("http://127.0.0.1:" + closedPort + "/hook", "{}", Map.of());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error()).startsWith("Could not reach endpoint");
    }

    @Test
    void nonHttpUrlsAreRefusedWithoutConnecting() {
        assertThat(client.post("file:///etc/passwd", "{}", Map.of()).error())
                .isEqualTo("Webhook URL must use http or https");
        assertThat(client.post("not a url", "{}", Map.of()).error()).isEqualTo("Webhook URL is not valid");
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
    }

    private static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
