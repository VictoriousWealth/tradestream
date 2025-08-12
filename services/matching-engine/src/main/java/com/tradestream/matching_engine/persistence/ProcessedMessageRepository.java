// persistence/ProcessedMessageRepository.java
package com.tradestream.matching_engine.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tradestream.matching_engine.domain.ProcessedMessage;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, Long> { // PK type changed
    boolean existsByTopicAndMessageId(String topic, UUID messageId); // NEW
}
