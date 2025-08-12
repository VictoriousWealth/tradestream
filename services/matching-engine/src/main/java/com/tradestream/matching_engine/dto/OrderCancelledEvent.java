// dto/OrderCancelledEvent.java
package com.tradestream.matching_engine.dto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderCancelledEvent {
    private UUID orderId;
    private UUID userId;
    private String ticker;
    private BigDecimal quantity;
    private BigDecimal price;
    private Instant timestamp;
}
