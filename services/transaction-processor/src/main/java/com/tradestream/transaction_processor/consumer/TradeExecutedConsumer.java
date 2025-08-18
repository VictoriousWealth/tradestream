package com.tradestream.transaction_processor.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.tradestream.transaction_processor.service.TransactionService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TradeExecutedConsumer {

    private final TransactionService transactionService;

    @KafkaListener(topics = "${tradestream.topics.tradeExecuted}")
    public void consume(ConsumerRecord<String, TradeExecutedEvent> record) {
        TradeExecutedEvent event = record.value();
        transactionService.processTrade(record.topic(), record.key(), event);
    }
}
