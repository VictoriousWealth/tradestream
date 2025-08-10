package com.tradestream.orders_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TradeExecuted(
        UUID tradeId,
        UUID orderId,
        UUID userId,
        String ticker,
        BigDecimal price,
        BigDecimal quantity,
        String side,          
        Instant timestamp
) {}
