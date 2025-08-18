package com.tradestream.portfolio_service.consumer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.tradestream.portfolio_service.config.TopicsProps;
import com.tradestream.portfolio_service.dto.TransactionRecordedEvent;
import com.tradestream.portfolio_service.service.PortfolioProjector;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransactionRecordedConsumer {

  private final PortfolioProjector projector;
  private final TopicsProps topics;

  @KafkaListener(
      topics = "#{topicsProps.transactionRecorded}",
      containerFactory = "kafkaListenerContainerFactory",
      properties = {
        "spring.json.value.default.type=com.tradestream.portfolio_service.dto.TransactionRecordedEvent",
        "spring.json.trusted.packages=com.tradestream.*",
        "spring.json.use.type.headers=false"
      }
  )
  public void onMessage(ConsumerRecord<String, TransactionRecordedEvent> rec,
                        @Header(name = KafkaHeaders.ACKNOWLEDGMENT, required = false) Acknowledgment ack,
                        @Header(name = "eventId", required = false) byte[] eventIdHeader) {

    TransactionRecordedEvent evt = rec.value();
    UUID msgId = evt.getEventId();
    if (msgId == null && eventIdHeader != null) {
      try { msgId = UUID.fromString(new String(eventIdHeader, StandardCharsets.UTF_8)); } catch (Exception ignore) {}
    }
    // last resort – stable synthetic id from partition/offset
    if (msgId == null) {
      String rid = rec.topic() + "|" + rec.partition() + "|" + rec.offset();
      msgId = UUID.nameUUIDFromBytes(rid.getBytes(StandardCharsets.UTF_8));
    }

    projector.apply(rec.topic(), msgId, evt);
    if (ack != null) ack.acknowledge();
  }
}
