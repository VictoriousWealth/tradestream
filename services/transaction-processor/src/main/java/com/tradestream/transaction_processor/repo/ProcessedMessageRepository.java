package com.tradestream.transaction_processor.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tradestream.transaction_processor.domain.ProcessedMessage;
import com.tradestream.transaction_processor.domain.ProcessedMessage.Key;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, Key> { }
