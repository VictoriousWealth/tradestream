package com.tradestream.transaction_processor.transaction;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    
    List<Transaction> findByUserId(UUID userId);
    
    List<Transaction> findByUserIdAndTicker(UUID userId, String ticker);
    
    List<Transaction> findByUserIdOrderByCreatedAtDesc(UUID userId);
    
}
