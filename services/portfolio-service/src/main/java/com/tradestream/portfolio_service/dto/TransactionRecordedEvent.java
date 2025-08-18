package com.tradestream.portfolio_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @ToString
public class TransactionRecordedEvent {
  private UUID eventId;
  private UUID tradeId;
  private UUID orderId;
  private UUID userId;
  private String side;        // "BUY" | "SELL"
  private String ticker;
  private BigDecimal quantity;
  private BigDecimal price;
  /** Broker publishes seconds-with-nanos (e.g., 1755513800.000000000) */
  private BigDecimal executedAt;
  private Integer version;

  public Instant executedAtInstant() {
    if (executedAt == null) return null;
    long seconds = executedAt.longValue();
    BigDecimal frac = executedAt.remainder(BigDecimal.ONE);
    int nanos = frac.movePointRight(9).abs().intValue();
    return Instant.ofEpochSecond(seconds, nanos);
  }
}
