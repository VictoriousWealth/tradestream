package com.tradestream.orders_service.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "orders")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(nullable = false, length = 16)
    private String ticker;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Side side;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private OrderType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_in_force", nullable = false, length = 16)
    private TimeInForce timeInForce;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal quantity;

    @Builder.Default
    @Column(name = "filled_quantity", nullable = false, precision = 18, scale = 6)
    private BigDecimal filledQuantity = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "last_fill_price", precision = 18, scale = 6) // nullable = true (default)
    private BigDecimal lastFillPrice = null; // start null; set when you get a fill price

    // nullable for MARKET; required for LIMIT
    @Column(precision = 18, scale = 6)
    private BigDecimal price;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
        if (status == null) status = OrderStatus.NEW;
        if (ticker != null) ticker = ticker.toUpperCase();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
        if (ticker != null) ticker = ticker.toUpperCase();
    }

    @Transient
    public BigDecimal remainingQuantity() {
        BigDecimal q = quantity != null ? quantity : BigDecimal.ZERO;
        BigDecimal f = filledQuantity != null ? filledQuantity : BigDecimal.ZERO;
        return q.subtract(f);
    }

    public void applyFill(BigDecimal execQty) {
        if (filledQuantity == null) filledQuantity = BigDecimal.ZERO;
        this.filledQuantity = this.filledQuantity.add(execQty);

        BigDecimal rem = remainingQuantity();
        this.status = rem.signum() <= 0 ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
    }

}