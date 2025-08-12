package com.tradestream.matching_engine.stream;

import java.nio.charset.StandardCharsets;

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
  public void onDlt(ConsumerRecord<String, byte[]> rec,
                    @Header(value = KafkaHeaders.DLT_EXCEPTION_FQCN, required = false) String exClass,
                    @Header(value = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String exMsg,
                    @Header(value = KafkaHeaders.DLT_ORIGINAL_TOPIC, required = false) String origTopic,
                    @Header(value = KafkaHeaders.DLT_ORIGINAL_OFFSET, required = false) Long origOffset) {

    String key = rec.key();
    String payload = rec.value() == null ? "null" : new String(rec.value(), StandardCharsets.UTF_8);

    System.err.printf(
        "DLT from %s@%s | %s: %s | key=%s | value=%s%n",
        origTopic, String.valueOf(origOffset), String.valueOf(exClass), String.valueOf(exMsg),
        String.valueOf(key), payload
    );
  }
}
