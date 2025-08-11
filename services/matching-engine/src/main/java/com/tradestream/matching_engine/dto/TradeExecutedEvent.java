// dto/TradeExecutedEvent.java
package com.tradestream.matching_engine.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TradeExecutedEvent {
    private UUID tradeId;
    private UUID buyOrderId;
    private UUID sellOrderId;
    private String ticker;
    private BigDecimal price;
    private BigDecimal quantity;
    private OffsetDateTime timestamp; // UTC
}
