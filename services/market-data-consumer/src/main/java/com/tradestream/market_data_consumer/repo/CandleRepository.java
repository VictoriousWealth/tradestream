package com.tradestream.market_data_consumer.repo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.tradestream.market_data_consumer.domain.Candle;

import jakarta.transaction.Transactional;

@Repository
public interface CandleRepository extends JpaRepository<Candle, UUID> {

    // Latest candle for a ticker/interval
    Optional<Candle> findFirstByTickerIgnoreCaseAndIntervalOrderByBucketStartDesc(String ticker, String interval);

    // Page through recent candles (newest first)
    @Query("SELECT c FROM Candle c " +
           "WHERE UPPER(c.ticker) = UPPER(:ticker) AND c.interval = :interval " +
           "ORDER BY c.bucketStart DESC")
    List<Candle> findRecent(@Param("ticker") String ticker,
                            @Param("interval") String interval,
                            Pageable pageable);

    /**
     * Atomic upsert for OHLCV using Postgres ON CONFLICT.
     * - If row doesn't exist: inserts with open=high=low=close=price, volume=qty
     * - If row exists: high=max(high, price), low=min(low, price), close=price, volume += qty, updated_at=now()
     *
     * Returns 1 if inserted or updated.
     */
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO candles (id, ticker, interval, bucket_start, open, high, low, close, volume, updated_at)
        VALUES (gen_random_uuid(), :ticker, :interval, :bucketStart, :price, :price, :price, :price, :qty, NOW())
        ON CONFLICT (ticker, interval, bucket_start)
        DO UPDATE SET
          high = GREATEST(candles.high, EXCLUDED.high),
          low  = LEAST(candles.low,  EXCLUDED.low),
          close = EXCLUDED.close,
          volume = candles.volume + EXCLUDED.volume,
          updated_at = NOW()
        """, nativeQuery = true)
    int upsertCandle(@Param("ticker") String ticker,
                     @Param("interval") String interval,
                     @Param("bucketStart") Instant bucketStart,
                     @Param("price") BigDecimal price,
                     @Param("qty") BigDecimal qty);
}
