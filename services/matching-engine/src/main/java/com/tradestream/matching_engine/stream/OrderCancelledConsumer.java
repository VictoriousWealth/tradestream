// stream/OrderCancelledConsumer.java
package com.tradestream.matching_engine.stream;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.tradestream.matching_engine.domain.ProcessedMessage;
import com.tradestream.matching_engine.dto.OrderCancelledEvent;
import com.tradestream.matching_engine.matching.MatchingService;
import com.tradestream.matching_engine.persistence.ProcessedMessageRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OrderCancelledConsumer {
    private final MatchingService matchingService;
    private final ProcessedMessageRepository msgRepo;

    @KafkaListener(
        topics = "#{engineProps.orderCancelled}", 
        containerFactory = "kafkaListenerContainerFactory",
        properties = {
            "spring.json.value.default.type=com.tradestream.matching_engine.dto.OrderCancelledEvent",
            "spring.json.trusted.packages=com.tradestream.*",
            "spring.json.use.type.headers=false"
        }
    )
    @Transactional
    public void onMessage(ConsumerRecord<String, OrderCancelledEvent> rec, Acknowledgment ack) {
        OrderCancelledEvent evt = rec.value();
        UUID messageId = extractMessageId(rec, Optional.ofNullable(evt.getOrderId()).orElse(UUID.randomUUID()));
        if (msgRepo.existsById(messageId)) { ack.acknowledge(); return; }

        if (evt.getOrderId() != null) matchingService.cancel(evt.getOrderId());
        msgRepo.save(ProcessedMessage.builder().messageId(messageId).receivedAt(OffsetDateTime.now()).build());
        ack.acknowledge();
    }

    private UUID extractMessageId(ConsumerRecord<?,?> rec, UUID fallback) {
        var header = rec.headers().lastHeader("eventId");
        if (header != null) try { return UUID.fromString(new String(header.value())); } catch (Exception ignore) {}
        return fallback;
    }
}
