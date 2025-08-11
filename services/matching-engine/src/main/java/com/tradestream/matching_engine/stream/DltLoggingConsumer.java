// src/main/java/com/tradestream/matching_engine/stream/DltLoggingConsumer.java
package com.tradestream.matching_engine.stream;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class DltLoggingConsumer {

  @KafkaListener(
      topics = {"#{engineProps.orderPlaced}.DLT", "#{engineProps.orderCancelled}.DLT"},
      groupId = "matching-engine-dlt-logger",
      containerFactory = "bytesKafkaListenerContainerFactory",
      properties = { "auto.offset.reset=latest" }
  )
  public void onDlt(ConsumerRecord<byte[], byte[]> rec,
                    @Header(KafkaHeaders.DLT_EXCEPTION_FQCN) String exClass,
                    @Header(KafkaHeaders.DLT_EXCEPTION_MESSAGE) String exMsg,
                    @Header(KafkaHeaders.DLT_ORIGINAL_TOPIC) String origTopic,
                    @Header(KafkaHeaders.DLT_ORIGINAL_OFFSET) Long origOffset) {

    String key = rec.key() == null ? "null" : new String(rec.key(), java.nio.charset.StandardCharsets.UTF_8);
    String payload = rec.value() == null ? "null" : new String(rec.value(), java.nio.charset.StandardCharsets.UTF_8);

    System.err.printf("DLT from %s@%d | %s: %s | key=%s | value=%s%n",
        origTopic, origOffset, exClass, exMsg, key, payload);
  }
}
