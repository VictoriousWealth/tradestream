package com.tradestream.transaction_processor.producer;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TransactionRecordedProducer {

    private final KafkaTemplate<String, TransactionRecordedEvent> kafkaTemplate;
    private final TopicProperties topicProperties;

    public void publish(TransactionRecordedEvent event) {
        // Use a stable key to keep ordering by user across partitions if desired.
        // You could also use userId or tradeId; here we use "tradeId:userId:side".
        String key = event.getTradeId() + ":" + event.getUserId() + ":" + event.getSide();
        kafkaTemplate.send(topicProperties.getTransactionRecorded(), key, event);
    }

    @Component
    @lombok.Getter
    @lombok.RequiredArgsConstructor
    public static class TopicProperties {
        private final org.springframework.core.env.Environment env;

        public String getTransactionRecorded() {
            return env.getProperty("tradestream.topics.transactionRecorded", "transaction.recorded.v1");
        }
    }
}
