package com.tradestream.transaction_processor.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "transactions",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_trade_participant", columnNames = {"trade_id", "user_id", "side"})
    }
)
@Getter @Setter
@Builder
@NoArgsConstructor @AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "trade_id", nullable = false)
    private UUID tradeId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 4)
    private Side side; // BUY or SELL

    @Column(name = "ticker", nullable = false, length = 32)
    private String ticker;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 6) // <—
    private java.math.BigDecimal quantity;

    @Column(name = "price", nullable = false, precision = 18, scale = 6)
    private java.math.BigDecimal price;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    public enum Side { BUY, SELL }
}
