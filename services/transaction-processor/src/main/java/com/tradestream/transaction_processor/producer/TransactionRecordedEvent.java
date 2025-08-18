package com.tradestream.transaction_processor.producer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransactionRecordedEvent {
    private UUID eventId;     // new UUID per event
    private UUID tradeId;
    private UUID orderId;
    private UUID userId;
    private String side;      // "BUY" | "SELL"
    private String ticker;
    private int quantity;     // positive
    private BigDecimal price; // scale matches DB (18,6)
    private Instant executedAt;
    private int version;      // schema version, start at 1
}
