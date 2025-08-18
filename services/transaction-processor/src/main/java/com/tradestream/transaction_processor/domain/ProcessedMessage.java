package com.tradestream.transaction_processor.domain;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "processed_messages")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class ProcessedMessage {

    @EmbeddedId
    private Key id;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt = Instant.now();

    @Embeddable
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        @Column(name = "topic", nullable = false)
        private String topic;

        @Column(name = "message_id", nullable = false)
        private String messageId;
    }
}
