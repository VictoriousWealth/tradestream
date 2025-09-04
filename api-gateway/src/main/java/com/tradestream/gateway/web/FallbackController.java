package com.tradestream.gateway.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import java.util.Map;

@RestController
public class FallbackController {
  @RequestMapping(value = "/fallback", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<Map<String, Object>> fallback() {
    return Mono.just(Map.of("status", "degraded", "message", "Downstream service unavailable. Please retry."));
  }
}

