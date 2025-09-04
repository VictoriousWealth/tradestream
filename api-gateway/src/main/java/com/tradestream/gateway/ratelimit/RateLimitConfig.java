package com.tradestream.gateway.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {
  @Bean
  public KeyResolver ipKeyResolver() {
    return exchange -> Mono.justOrEmpty(
      exchange.getRequest().getHeaders().getFirst("X-Forwarded-For")
    ).switchIfEmpty(Mono.just(exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()));
  }
}

