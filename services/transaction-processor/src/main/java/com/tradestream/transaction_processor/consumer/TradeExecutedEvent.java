package com.tradestream.transaction_processor.consumer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import lombok.Data;

@Data
public class TradeExecutedEvent {
    private UUID tradeId;
    private UUID buyOrderId;
    private UUID sellOrderId;
    private String ticker;
    private BigDecimal price;     // keep BigDecimal
    private BigDecimal quantity;  // <— was int
    private Instant timestamp;    // ISO-8601 instant (producer side decides format)
}
