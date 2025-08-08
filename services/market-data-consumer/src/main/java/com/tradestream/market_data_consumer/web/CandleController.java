package com.tradestream.market_data_consumer.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tradestream.market_data_consumer.agg.Interval;
import com.tradestream.market_data_consumer.domain.Candle;
import com.tradestream.market_data_consumer.service.AggregationService;
import com.tradestream.market_data_consumer.service.CandleQueryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/candles")
public class CandleController {

    private final AggregationService agg;
    private final CandleQueryService query;
    
    @GetMapping("/{ticker}")
    public ResponseEntity<List<Candle>> recent(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "1m") String interval,
            @RequestParam(defaultValue = "100") int limit
    ) {
        // Validate interval
        Interval.fromCode(interval);
        var list = (List<Candle>) (List<?>) agg.recentCandles(ticker, interval, Math.min(limit, 1000));
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{ticker}/latest")
    public ResponseEntity<Candle> latest(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "1m") String interval
    ) {
        var c = query.latest(ticker, interval);
        return (c == null) ? ResponseEntity.notFound().build() : ResponseEntity.ok(c);
    }
}
