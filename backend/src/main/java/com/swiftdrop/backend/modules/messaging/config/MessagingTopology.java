package com.swiftdrop.backend.modules.messaging.config;

import java.time.Duration;

/**
 * Names of every exchange, queue and routing key used for events and webhooks, so
 * producers, consumers and the broker setup never disagree on a string.
 *
 * <pre>
 * swiftdrop.events (topic) --parcel.#--> swiftdrop.webhooks.events --WebhookConsumer
 *                          \--parcel.#--> (notification queue, owned by the notifications module)
 *
 * swiftdrop.webhooks (direct) --retry.&lt;ms&gt;--> swiftdrop.webhooks.retry.&lt;ms&gt; (TTL)
 *                             &lt;--attempt-- (dead-lettered back when the TTL expires)
 *                             --attempt--> swiftdrop.webhooks.attempts --WebhookConsumer
 *
 * swiftdrop.dlx (direct) --webhooks--> swiftdrop.webhooks.dlq   (rejected / exhausted messages)
 * </pre>
 */
public final class MessagingTopology {

    /** Every domain event is published here; bind your own queue to receive them. */
    public static final String EVENTS_EXCHANGE = "swiftdrop.events";

    public static final String WEBHOOK_EVENTS_QUEUE = "swiftdrop.webhooks.events";
    public static final String WEBHOOK_EVENTS_BINDING = "parcel.#";

    public static final String WEBHOOK_EXCHANGE = "swiftdrop.webhooks";
    public static final String WEBHOOK_ATTEMPTS_QUEUE = "swiftdrop.webhooks.attempts";
    public static final String WEBHOOK_ATTEMPT_KEY = "attempt";

    public static final String DEAD_LETTER_EXCHANGE = "swiftdrop.dlx";
    public static final String WEBHOOK_DEAD_LETTER_QUEUE = "swiftdrop.webhooks.dlq";
    public static final String WEBHOOK_DEAD_LETTER_KEY = "webhooks";

    /** Listener container factory that dead-letters failed messages instead of requeueing them. */
    public static final String WEBHOOK_LISTENER_FACTORY = "webhookListenerContainerFactory";

    private MessagingTopology() {
    }

    /** The delay is part of the name because RabbitMQ cannot change a queue's TTL once declared. */
    public static String retryQueue(Duration delay) {
        return "swiftdrop.webhooks.retry." + delay.toMillis() + "ms";
    }

    public static String retryRoutingKey(Duration delay) {
        return "retry." + delay.toMillis() + "ms";
    }
}
