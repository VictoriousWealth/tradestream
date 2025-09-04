package com.tradestream.gateway.filters;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import java.util.UUID;

@Configuration
public class RequestIdFilter {
  public static final String HEADER = "X-Request-Id";
  @Bean
  public GlobalFilter addRequestIdHeaderFilter() {
    return (exchange, chain) -> {
      ServerHttpRequest req = exchange.getRequest();
      if (!req.getHeaders().containsKey(HEADER)) {
        var mutated = req.mutate().header(HEADER, UUID.randomUUID().toString()).build();
        ServerWebExchange ex2 = exchange.mutate().request(mutated).build();
        return chain.filter(ex2);
      }
      return chain.filter(exchange);
    };
  }
}

