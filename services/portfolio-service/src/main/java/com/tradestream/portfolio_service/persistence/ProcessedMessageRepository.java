package com.tradestream.portfolio_service.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tradestream.portfolio_service.domain.ProcessedMessage;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, Long> {
  boolean existsByTopicAndMessageId(String topic, UUID messageId);
}
