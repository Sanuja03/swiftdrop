package com.swiftdrop.backend.modules.notifications.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationRabbitMqConfig {

    public static final String NOTIFICATION_QUEUE =
            "swiftdrop.notifications.events";
    public static final String MERCHANT_NOTIFICATION_QUEUE = "swiftdrop.notifications.merchants";
    public static final String REJECTION_EMAIL_QUEUE = "swiftdrop.notifications.merchant-rejections";
    public static final String REJECTION_EMAIL_DLQ = REJECTION_EMAIL_QUEUE + ".dlq";
    public static final String APPROVAL_EMAIL_QUEUE = "swiftdrop.notifications.merchant-approvals";
    public static final String DECISION_NOTIFICATION_QUEUE = "swiftdrop.notifications.merchant-decisions";

    @Bean
    public org.springframework.amqp.core.Declarables merchantDecisionChannels() {
        String exchange = com.swiftdrop.backend.modules.messaging.config.MessagingTopology.EVENTS_EXCHANGE;
        String dlx = com.swiftdrop.backend.modules.messaging.config.MessagingTopology.DEAD_LETTER_EXCHANGE;
        var items = new java.util.ArrayList<org.springframework.amqp.core.Declarable>();
        for (String queue : new String[]{APPROVAL_EMAIL_QUEUE, DECISION_NOTIFICATION_QUEUE}) {
            items.add(QueueBuilder.durable(queue).deadLetterExchange(dlx).deadLetterRoutingKey(queue).build());
            items.add(QueueBuilder.durable(queue + ".dlq").build());
            items.add(new Binding(queue + ".dlq", Binding.DestinationType.QUEUE, dlx, queue, null));
            items.add(new Binding(queue, Binding.DestinationType.QUEUE, exchange, "merchant.approved", null));
        }
        items.add(new Binding(DECISION_NOTIFICATION_QUEUE, Binding.DestinationType.QUEUE, exchange, "merchant.rejected", null));
        return new org.springframework.amqp.core.Declarables(items);
    }

    @Bean
    public Queue merchantRejectionEmailQueue() {
        return QueueBuilder.durable(REJECTION_EMAIL_QUEUE)
                .deadLetterExchange(com.swiftdrop.backend.modules.messaging.config.MessagingTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(REJECTION_EMAIL_QUEUE).build();
    }

    @Bean
    public Queue merchantRejectionEmailDeadLetterQueue() {
        return QueueBuilder.durable(REJECTION_EMAIL_DLQ).build();
    }

    @Bean
    public Binding merchantRejectionEmailBinding(TopicExchange eventsExchange) {
        return new Binding(REJECTION_EMAIL_QUEUE, Binding.DestinationType.QUEUE,
                eventsExchange.getName(), "merchant.rejected", null);
    }

    @Bean
    public Binding merchantRejectionEmailDeadLetterBinding() {
        return new Binding(REJECTION_EMAIL_DLQ, Binding.DestinationType.QUEUE,
                com.swiftdrop.backend.modules.messaging.config.MessagingTopology.DEAD_LETTER_EXCHANGE,
                REJECTION_EMAIL_QUEUE, null);
    }

    @Bean
    public Queue merchantNotificationEventsQueue() {
        return QueueBuilder.durable(MERCHANT_NOTIFICATION_QUEUE).build();
    }

    @Bean
    public Binding merchantNotificationEventsBinding(TopicExchange eventsExchange) {
        return new Binding(MERCHANT_NOTIFICATION_QUEUE, Binding.DestinationType.QUEUE,
                eventsExchange.getName(), "merchant.registered", null);
    }

    @Bean
    public Queue notificationEventsQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE).build();
    }

    @Bean
    public Binding notificationEventsBinding(
            Queue notificationEventsQueue,
            TopicExchange eventsExchange
    ) {
        return BindingBuilder.bind(notificationEventsQueue)
                .to(eventsExchange)
                .with("parcel.#");
    }
}
