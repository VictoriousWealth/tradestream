package com.tradestream.orders_service.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ingested_fills")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class IngestedFill {

  @EmbeddedId
  private IngestedFillId id;

  @Column(nullable = false, length = 16)
  private String ticker;

  @Column(name = "ts", nullable = false)
  private Instant ts;
}
