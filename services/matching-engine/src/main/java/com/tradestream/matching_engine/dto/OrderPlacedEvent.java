// dto/OrderPlacedEvent.java
package com.tradestream.matching_engine.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tradestream.matching_engine.domain.OrderSide;
import com.tradestream.matching_engine.domain.OrderType;
import com.tradestream.matching_engine.domain.TimeInForce;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderPlacedEvent {
    private UUID orderId;
    private UUID userId;
    private String ticker;
    private OrderSide side;
    @JsonProperty("orderType") @JsonAlias({"type","orderType"}) private OrderType orderType;
    private TimeInForce timeInForce;
    private BigDecimal price;     // null for MARKET
    private BigDecimal quantity;
    Instant timestamp;
}
