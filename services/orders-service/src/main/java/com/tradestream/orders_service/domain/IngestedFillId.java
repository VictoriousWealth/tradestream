package com.tradestream.orders_service.domain;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode
public class IngestedFillId implements Serializable {
  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(name = "trade_id", nullable = false)
  private UUID tradeId;
}
