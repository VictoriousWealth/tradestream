// domain/ProcessedMessage.java
package com.tradestream.matching_engine.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "processed_messages")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProcessedMessage {
    @Id @Column(name="message_id") private UUID messageId;
    @Column(name="received_at", nullable=false) private OffsetDateTime receivedAt;
}
