package com.tradestream.market_data_consumer.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "ingested_trades",
    indexes = {
        @Index(name = "idx_ingested_trades_ticker_ts", columnList = "ticker, ts DESC")
    }
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngestedTrade {

    @Id
    @Column(name = "trade_id", nullable = false, updatable = false)
    private UUID tradeId;

    @Column(nullable = false)
    private String ticker;

    @Column(name = "ts", nullable = false)
    private Instant timestamp;
}
