package com.tradestream.orders_service.repo;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.tradestream.orders_service.domain.IngestedFill;
import com.tradestream.orders_service.domain.IngestedFillId;

public interface IngestedFillRepository extends JpaRepository<IngestedFill, IngestedFillId> {

  @Modifying
  @Transactional
  @Query(value = """
    INSERT INTO ingested_fills(order_id, trade_id, ticker, ts)
    VALUES (:orderId, :tradeId, :ticker, :ts)
    ON CONFLICT (order_id, trade_id) DO NOTHING
    """, nativeQuery = true)
  int tryInsert(@Param("orderId") UUID orderId,
                @Param("tradeId") UUID tradeId,
                @Param("ticker") String ticker,
                @Param("ts") Instant ts);
}
