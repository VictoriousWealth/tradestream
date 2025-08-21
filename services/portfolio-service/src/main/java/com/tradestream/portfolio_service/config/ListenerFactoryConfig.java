package com.tradestream.portfolio_service.config;

import com.tradestream.portfolio_service.dto.TransactionRecordedEvent;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;

@Configuration
public class ListenerFactoryConfig {

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, TransactionRecordedEvent>
      kafkaListenerContainerFactory(ConsumerFactory<String, TransactionRecordedEvent> cf,
                                    DefaultErrorHandler errorHandler) {
    var f = new ConcurrentKafkaListenerContainerFactory<String, TransactionRecordedEvent>();
    f.setConsumerFactory(cf);
    f.setCommonErrorHandler(errorHandler);
    f.setConcurrency(1); // deterministic updates
    f.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
    return f;
  }
}
