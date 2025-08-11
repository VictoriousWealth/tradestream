// dto/OrderPlacedEvent.java
package com.tradestream.matching_engine.dto;

import java.math.BigDecimal;
import java.util.UUID;

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
    private OrderType orderType;
    private TimeInForce timeInForce;
    private BigDecimal price;     // null for MARKET
    private BigDecimal quantity;
}
