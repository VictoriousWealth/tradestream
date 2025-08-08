package com.tradestream.market_data_consumer.repo;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.tradestream.market_data_consumer.domain.IngestedTrade;

import jakarta.transaction.Transactional;

@Repository
public interface IngestedTradeRepository extends JpaRepository<IngestedTrade, UUID> {

    /**
     * Try to record a tradeId; returns 1 if inserted, 0 if duplicate.
     * We use this for idempotency before updating candles.
     */
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO ingested_trades (trade_id, ticker, ts)
        VALUES (:tradeId, :ticker, :ts)
        ON CONFLICT (trade_id) DO NOTHING
        """, nativeQuery = true)
    int tryInsert(@Param("tradeId") UUID tradeId,
                  @Param("ticker") String ticker,
                  @Param("ts") Instant ts);
}
