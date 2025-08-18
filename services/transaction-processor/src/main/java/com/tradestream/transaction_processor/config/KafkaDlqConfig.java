package com.tradestream.transaction_processor.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration
public class KafkaDlqConfig {

    @Bean
    DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaTemplate<Object, Object> template) {
        // Sends to <originalTopic>.DLT on same partition
        return new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
    }

    @Bean
    DefaultErrorHandler errorHandler(DeadLetterPublishingRecoverer dlpr) {
        var backoff = new ExponentialBackOffWithMaxRetries(5);
        backoff.setInitialInterval(200);
        backoff.setMultiplier(2.0);
        backoff.setMaxInterval(5_000);
        var h = new DefaultErrorHandler(dlpr, backoff);
        h.addNotRetryableExceptions(IllegalArgumentException.class);
        return h;
    }
}
