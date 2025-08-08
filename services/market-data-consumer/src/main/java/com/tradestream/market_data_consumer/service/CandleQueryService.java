package com.tradestream.market_data_consumer.service;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.tradestream.market_data_consumer.domain.Candle;
import com.tradestream.market_data_consumer.repo.CandleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CandleQueryService {

    private final CandleRepository repo;

    // Cache the latest candle per (ticker, interval)
    @Cacheable(cacheNames = "latest", key = "T(java.lang.String).format('%s:%s', #interval, #ticker.toUpperCase())")
    public Candle latest(String ticker, String interval) {
        return repo.findFirstByTickerIgnoreCaseAndIntervalOrderByBucketStartDesc(ticker, interval)
                   .orElse(null); // cache-null-values=false, so null not cached
    }

    // not cached; tweak if needed
    public List<?> recent(String ticker, String interval, int limit) {
        return repo.findRecent(ticker, interval, PageRequest.of(0, limit));
    }
}
