package com.tradestream.portfolio_service.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
  name = "processed_messages",
  uniqueConstraints = @UniqueConstraint(
    name = "uk_processed_topic_msgid",
    columnNames = {"topic", "message_id"}
  )
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProcessedMessage {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(nullable=false, length=200) private String topic;
  @Column(name="message_id", nullable=false) private UUID messageId;
  @Column(name="received_at", nullable=false) private OffsetDateTime receivedAt;
}
