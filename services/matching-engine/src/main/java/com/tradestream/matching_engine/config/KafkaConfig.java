// config/KafkaConfig.java
package com.tradestream.matching_engine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class KafkaConfig {
    @Bean public ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
}
