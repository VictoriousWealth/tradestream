package com.tradestream.matching_engine.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

@Configuration
public class BytesListenerFactoryConfig {

  @Bean("bytesConsumerFactory")
  public ConsumerFactory<String, byte[]> bytesConsumerFactory(KafkaProperties kafkaProps) {
    // Build a fresh, independent config from application properties
    Map<String, Object> props = new HashMap<>(kafkaProps.buildConsumerProperties());
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
    // Optional: ensure we can read older DLT messages if group is new
    props.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    return new DefaultKafkaConsumerFactory<>(props);
  }

  @Bean("bytesKafkaListenerContainerFactory")
  public ConcurrentKafkaListenerContainerFactory<String, byte[]> bytesKafkaListenerContainerFactory(
      ConsumerFactory<String, byte[]> cf) {
    var f = new ConcurrentKafkaListenerContainerFactory<String, byte[]>();
    f.setConsumerFactory(cf);
    // Keep error handler simple to avoid wiring in more beans and creating new cycles
    return f;
  }
}
