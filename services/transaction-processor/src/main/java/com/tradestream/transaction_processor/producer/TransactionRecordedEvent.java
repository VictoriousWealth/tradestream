package com.tradestream.transaction_processor.producer;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransactionRecordedEvent {
    private UUID eventId;
    private UUID tradeId;
    private UUID orderId;
    private UUID userId;
    private String side;
    private String ticker;
    private java.math.BigDecimal quantity;
    private java.math.BigDecimal price;
    private java.time.Instant executedAt;
    private int version;
}
