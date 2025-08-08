package com.tradestream.market_data_consumer.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tradestream.market_data_consumer.agg.Bucketizer;
import com.tradestream.market_data_consumer.agg.Interval;
import com.tradestream.market_data_consumer.cache.CacheOps;
import com.tradestream.market_data_consumer.dto.TradeExecuted;
import com.tradestream.market_data_consumer.repo.CandleRepository;
import com.tradestream.market_data_consumer.repo.IngestedTradeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AggregationService {

    private static final EnumSet<Interval> SUPPORTED = EnumSet.of(
        Interval.ONE_MIN, Interval.FIVE_MIN, Interval.ONE_HOUR, Interval.ONE_DAY
    );

    private final IngestedTradeRepository ingestedRepo;
    private final CandleRepository candleRepo;
    private final CacheOps cacheOps; // ✅ use proxy’d bean for @CacheEvict

    /**
     * Idempotent: returns false if duplicate trade, true if aggregated.
     */
    @Transactional
    public boolean process(TradeExecuted t) {
        int inserted = ingestedRepo.tryInsert(t.tradeId(), t.ticker(), t.timestamp());
        if (inserted == 0) return false; // duplicate

        String ticker = t.ticker().toUpperCase(Locale.ROOT);
        BigDecimal price = BigDecimal.valueOf(t.price());
        BigDecimal qty   = BigDecimal.valueOf(t.quantity());

        for (Interval itv : SUPPORTED) {
            Instant bucket = Bucketizer.bucketStart(t.timestamp(), itv);
            candleRepo.upsertCandle(ticker, itv.code(), bucket, price, qty);

            // ✅ Evict the cache key "1m:AAPL", "5m:AAPL", etc.
            cacheOps.evictLatest(ticker, itv.code());
        }
        return true;
    }

    public List<?> recentCandles(String ticker, String interval, int limit) {
        return candleRepo.findRecent(ticker, interval, PageRequest.of(0, limit));
    }
}
