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
import com.tradestream.matching_engine.dto.OrderCancelledEvent;
import com.tradestream.matching_engine.matching.MatchingService;
import com.tradestream.matching_engine.persistence.ProcessedMessageRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OrderCancelledConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderCancelledConsumer.class);

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
        log.info("CANCEL CONSUMED topic={} key={} partition={} offset={} value={}",
                rec.topic(), rec.key(), rec.partition(), rec.offset(), rec.value());

        OrderCancelledEvent evt = rec.value();
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

        if (evt.getOrderId() != null) {
            log.info("APPLYING CANCEL orderId={} ticker={} qty={} price={}",
                    evt.getOrderId(), evt.getTicker(), evt.getQuantity(), evt.getPrice());
            matchingService.cancel(evt.getOrderId());
        } else {
            log.warn("CANCEL event missing orderId, ignoring");
        }

        msgRepo.save(ProcessedMessage.builder()
                .topic(rec.topic())
                .messageId(messageId)
                .receivedAt(OffsetDateTime.now())
                .build());

        ack.acknowledge();
    }

    private UUID extractMessageId(ConsumerRecord<?, ?> rec, UUID fallback) {
        var header = rec.headers().lastHeader("eventId");
        if (header != null) {
            try {
                return UUID.fromString(new String(header.value()));
            } catch (Exception e) {
                log.warn("Invalid eventId header for record key={}, using fallback", rec.key(), e);
            }
        }
        return fallback;
    }
}
