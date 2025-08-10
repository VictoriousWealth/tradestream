package com.tradestream.orders_service.repo;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.tradestream.orders_service.domain.IngestedTrade;

public interface IngestedTradeRepository extends JpaRepository<IngestedTrade, UUID> {

    @Modifying
    @Query(value = """
        INSERT INTO ingested_trades (trade_id, order_id, ticker, ts)
        VALUES (?1, ?2, ?3, ?4)
        ON CONFLICT (trade_id) DO NOTHING
        """, nativeQuery = true)
    int tryInsert(UUID tradeId, UUID orderId, String ticker, Instant ts);
}
