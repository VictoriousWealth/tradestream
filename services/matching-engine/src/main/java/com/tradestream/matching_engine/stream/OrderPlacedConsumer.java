// stream/OrderPlacedConsumer.java
package com.tradestream.matching_engine.stream;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    
    private static final Logger log = LoggerFactory.getLogger(OrderPlacedConsumer.class);
    
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
        
        UUID messageId = extractMessageId(rec, null);
        if (messageId == null) {
            String rid = rec.topic() + "|" + rec.partition() + "|" + rec.offset();
            messageId = UUID.nameUUIDFromBytes(rid.getBytes(StandardCharsets.UTF_8));
        }

        if (msgRepo.existsByTopicAndMessageId(rec.topic(), messageId)) {
            log.info("DUP topic={} messageId={} - skipping", rec.topic(), messageId);
            ack.acknowledge();
            return;
        }

        matchingService.handleIncoming(evt);
        msgRepo.save(ProcessedMessage.builder()
                .topic(rec.topic())
                .messageId(messageId)
                .receivedAt(OffsetDateTime.now())
                .build());

        ack.acknowledge();
    }

    private UUID extractMessageId(ConsumerRecord<?,?> rec, UUID fallback) {
        // Use header "eventId" if present; else fallback to orderId; ensures idempotency across retries.
        var header = rec.headers().lastHeader("eventId");
        if (header != null) try { return UUID.fromString(new String(header.value())); } catch (Exception ignore) {}
        return fallback;
    }
}
