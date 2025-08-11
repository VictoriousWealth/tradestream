// src/main/java/com/tradestream/matching_engine/config/KafkaDlqConfig.java
package com.tradestream.matching_engine.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration
public class KafkaDlqConfig {

    // Publishes failed records to <originalTopic>.DLT, same partition
    @Bean
    DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaTemplate<Object, Object> template) {
        return new DeadLetterPublishingRecoverer(
            template,
            (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition())
        );
    }

    // Retry with backoff, then send to DLT
    @Bean
    DefaultErrorHandler errorHandler(DeadLetterPublishingRecoverer dlpr) {
        var backoff = new ExponentialBackOffWithMaxRetries(5); // 5 retries
        backoff.setInitialInterval(200);   // 200 ms
        backoff.setMultiplier(2.0);        // 200, 400, 800, 1600, 3200
        backoff.setMaxInterval(5000);      // cap at 5s
        var handler = new DefaultErrorHandler(dlpr, backoff);

        // Non-retryable (go straight to DLT)
        handler.addNotRetryableExceptions(IllegalArgumentException.class);

        return handler;
    }
}
