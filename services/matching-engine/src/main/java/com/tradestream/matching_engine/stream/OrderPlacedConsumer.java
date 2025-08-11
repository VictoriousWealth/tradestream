// stream/OrderPlacedConsumer.java
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
import com.tradestream.matching_engine.dto.OrderPlacedEvent;
import com.tradestream.matching_engine.matching.MatchingService;
import com.tradestream.matching_engine.persistence.ProcessedMessageRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OrderPlacedConsumer {
    private final MatchingService matchingService;
    private final ProcessedMessageRepository msgRepo;

    @KafkaListener(
        topics = "#{engineProps.orderPlaced}",
        containerFactory = "kafkaListenerContainerFactory",
        properties = {
            "spring.json.value.default.type=com.tradestream.matching_engine.dto.OrderPlacedEvent",
            "spring.json.trusted.packages=com.tradestream.*",
            "spring.json.use.type.headers=false"
        }
    )
    @Transactional
    public void onMessage(ConsumerRecord<String, OrderPlacedEvent> rec, Acknowledgment ack) {
        OrderPlacedEvent evt = rec.value();
        UUID messageId = extractMessageId(rec, Optional.ofNullable(evt.getOrderId()).orElse(UUID.randomUUID()));
        if (msgRepo.existsById(messageId)) { ack.acknowledge(); return; }

        matchingService.handleIncoming(evt);
        msgRepo.save(ProcessedMessage.builder().messageId(messageId).receivedAt(OffsetDateTime.now()).build());
        ack.acknowledge();
    }

    private UUID extractMessageId(ConsumerRecord<?,?> rec, UUID fallback) {
        // Use header "eventId" if present; else fallback to orderId; ensures idempotency across retries.
        var header = rec.headers().lastHeader("eventId");
        if (header != null) try { return UUID.fromString(new String(header.value())); } catch (Exception ignore) {}
        return fallback;
    }
}
