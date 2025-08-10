package com.tradestream.orders_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.tradestream.orders_service.domain.OrderStatus;
import com.tradestream.orders_service.domain.OrderType;
import com.tradestream.orders_service.domain.Side;
import com.tradestream.orders_service.domain.TimeInForce;

public record OrderResponse(
    UUID id,
    UUID userId,
    String ticker,
    Side side,
    OrderType type,
    TimeInForce timeInForce,
    BigDecimal quantity,
    BigDecimal price,
    OrderStatus status,
    BigDecimal filledQuantity,
    BigDecimal remainingQuantity,
    BigDecimal lastFillPrice,
    Instant createdAt,
    Instant updatedAt
) {}
