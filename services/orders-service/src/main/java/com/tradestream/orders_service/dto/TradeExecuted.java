// services/orders-service/src/main/java/com/tradestream/orders_service/dto/TradeExecuted.java
package com.tradestream.orders_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TradeExecuted(
    UUID tradeId,
    UUID buyOrderId,
    UUID sellOrderId,
    String ticker,
    BigDecimal price,
    BigDecimal quantity,
    Instant timestamp
) {}
