// src/main/java/com/tradestream/matching_engine/config/BytesListenerFactoryConfig.java
package com.tradestream.matching_engine.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;

@Configuration
public class BytesListenerFactoryConfig {

  @Bean("bytesConsumerFactory")
  public ConsumerFactory<byte[], byte[]> bytesConsumerFactory(
      ConsumerFactory<?, ?> base // Boot auto-config provides this
  ) {
    Map<String, Object> props = new HashMap<>(base.getConfigurationProperties());
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
    return new DefaultKafkaConsumerFactory<>(props);
  }

  @Bean("bytesKafkaListenerContainerFactory")
  public ConcurrentKafkaListenerContainerFactory<byte[], byte[]> bytesKafkaListenerContainerFactory(
      @Qualifier("bytesConsumerFactory") ConsumerFactory<byte[], byte[]> cf
  ) {
    var f = new ConcurrentKafkaListenerContainerFactory<byte[], byte[]>();
    f.setConsumerFactory(cf);
    // no custom error handler -> no DLT-for-DLT loops
    return f;
  }
}
