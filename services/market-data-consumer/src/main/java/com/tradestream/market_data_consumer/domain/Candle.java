package com.tradestream.market_data_consumer.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "candles",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_candles", columnNames = {"ticker", "interval", "bucket_start"})
    },
    indexes = {
        @Index(name = "idx_candles_ticker_interval_bucket", columnList = "ticker, interval, bucket_start DESC")
    }
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Candle {

    @Id
    @Column(nullable = false, updatable = false)
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    private String ticker;            // UPPERCASE

    @Column(name = "interval", nullable = false)
    private String interval;          // "1m" | "5m" | "1h" | "1d"

    @Column(name = "bucket_start", nullable = false)
    private Instant bucketStart;      // UTC boundary

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal open;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal high;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal low;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal close;

    @Column(nullable = false, precision = 20, scale = 6)
    @Builder.Default
    private BigDecimal volume = BigDecimal.ZERO;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        updatedAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
