package com.tradestream.orders_service.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "ingested_trades")
public class IngestedTrade {

    @Id
    @Column(name = "trade_id", nullable = false, updatable = false)
    private UUID tradeId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "ticker", nullable = false, length = 16)
    private String ticker;

    @Column(name = "ts", nullable = false)
    private Instant timestamp;

    protected IngestedTrade() {}

    public IngestedTrade(UUID tradeId, UUID orderId, String ticker, Instant timestamp) {
        this.tradeId = tradeId;
        this.orderId = orderId;
        this.ticker = ticker;
        this.timestamp = timestamp;
    }

}
