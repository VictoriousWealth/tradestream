package com.tradestream.portfolio_service.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "positions")
@IdClass(PositionId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Position {
  @Id @Column(name="user_id", nullable=false) private UUID userId;
  @Id @Column(name="ticker",  nullable=false, length=16) private String ticker;

  @Column(nullable=false, precision=18, scale=8) private BigDecimal quantity;
  @Column(name="avg_cost", precision=18, scale=8) private BigDecimal avgCost;
  @Column(name="realized_pnl", nullable=false, precision=18, scale=8) private BigDecimal realizedPnl;

  @Column(name="updated_at", nullable=false) private OffsetDateTime updatedAt;

  @PrePersist @PreUpdate
  void touch() { this.updatedAt = OffsetDateTime.now(); }
}
