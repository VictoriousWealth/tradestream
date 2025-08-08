package com.tradestream.market_data_consumer.cache;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

@Component
public class CacheOps {

    // Evict the exact key produced by @Cacheable: "%s:%s" -> "1m:AAPL"
    @CacheEvict(cacheNames = "latest", key = "#interval + ':' + #ticker")
    public void evictLatest(String ticker, String interval) {
        // no-op; annotation does the work
    }
}
