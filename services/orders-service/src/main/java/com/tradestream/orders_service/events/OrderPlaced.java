package com.tradestream.orders_service.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.tradestream.orders_service.domain.OrderType;
import com.tradestream.orders_service.domain.Side;
import com.tradestream.orders_service.domain.TimeInForce;

/** Payload sent to topic order.placed.v1 */
public record OrderPlaced(
        UUID orderId,
        UUID userId,
        String ticker,
        Side side,
        OrderType type,
        TimeInForce timeInForce,
        BigDecimal quantity,
        BigDecimal price,       // null for MARKET
        Instant timestamp       // event time (createdAt)
) { }
