package com.tradestream.orders_service.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderCancelledEvent {
    private UUID orderId;
    private UUID userId;
    private String ticker;
    private BigDecimal quantity;
    private BigDecimal price;
    @JsonFormat(shape = JsonFormat.Shape.STRING) private Instant timestamp;
}
