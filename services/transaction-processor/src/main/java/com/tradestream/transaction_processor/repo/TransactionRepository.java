package com.tradestream.transaction_processor.repo;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.tradestream.transaction_processor.domain.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Page<Transaction> findByUserId(UUID userId, Pageable pageable);

    Page<Transaction> findByUserIdAndTicker(UUID userId, String ticker, Pageable pageable);

    Page<Transaction> findByUserIdAndExecutedAtGreaterThanEqual(UUID userId, Instant since, Pageable pageable);
}
