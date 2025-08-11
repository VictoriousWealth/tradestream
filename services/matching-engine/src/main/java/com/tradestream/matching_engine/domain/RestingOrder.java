// src/main/java/com/tradestream/matching_engine/domain/RestingOrder.java
package com.tradestream.matching_engine.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "resting_orders")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class RestingOrder {
    @Id
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name="user_id", nullable=false) private UUID userId;
    @Column(nullable=false, length=16) private String ticker;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=4)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(name="order_type", nullable=false, length=10)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(name="time_in_force", nullable=false, length=10)
    private TimeInForce timeInForce;

    @Column(precision=18, scale=8)
    private BigDecimal price; // null for MARKET (but MARKET must never rest in the book)

    @Column(name="original_quantity", precision=18, scale=8, nullable=false)
    private BigDecimal originalQuantity;

    @Column(name="remaining_quantity", precision=18, scale=8, nullable=false)
    private BigDecimal remainingQuantity;

    @Column(nullable=false, length=16)
    private String status; // ACTIVE, PARTIALLY_FILLED, FILLED, CANCELED

    @Column(name="created_at", nullable=false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name="updated_at", nullable=false)
    private OffsetDateTime updatedAt;
}
