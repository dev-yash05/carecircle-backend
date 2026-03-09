package com.carecircle.config;

import com.carecircle.scheduler.OutboxPublisher;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // 🧠 TopicExchange: Routes messages by routing key pattern.
    // "dose.event" → goes to the notification queue
    // "vital.anomaly" → could go to an alert queue (future)
    @Bean
    public TopicExchange careCircleExchange() {
        return new TopicExchange(OutboxPublisher.EXCHANGE);
    }

    // The queue where dose notifications land
    @Bean
    public Queue doseEventQueue() {
        return QueueBuilder.durable("carecircle.dose.notifications")
                .withArgument("x-dead-letter-exchange", "carecircle.dlx")  // Dead letter queue
                .build();
    }

    // Bind queue to exchange with routing key
    @Bean
    public Binding doseEventBinding(Queue doseEventQueue, TopicExchange careCircleExchange) {
        return BindingBuilder
                .bind(doseEventQueue)
                .to(careCircleExchange)
                .with(OutboxPublisher.DOSE_ROUTING_KEY);
    }
}


















