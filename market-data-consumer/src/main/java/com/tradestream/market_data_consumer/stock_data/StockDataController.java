package com.tradestream.market_data_consumer.stock_data;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/stock")
public class StockDataController {

    private final StockDataService service;

    public StockDataController(StockDataService service) {
        this.service = service;
    }

    @PostMapping("/event")
    public ResponseEntity<Void> ingestMarketData(@Valid @RequestBody MarketDataEvent event) {
        service.processMarketEvent(event);
        return ResponseEntity.accepted().build();
    }

    @GetMapping
    public ResponseEntity<List<StockDataDto>> getAllLatestStocks() {
        return ResponseEntity.ok(service.getAllLatestStockDtos());
    }

    @GetMapping("/{ticker}")
    public ResponseEntity<StockDataDto> getLatestByTicker(@PathVariable String ticker) {
        return service.getLatestStockDtoByTicker(ticker)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }


}
