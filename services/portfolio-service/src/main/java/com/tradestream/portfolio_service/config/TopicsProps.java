package com.tradestream.portfolio_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
 import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "tradestream.topics")
@Getter @Setter
public class TopicsProps {
  private String tradeExecuted;
  private String transactionRecorded;
}
