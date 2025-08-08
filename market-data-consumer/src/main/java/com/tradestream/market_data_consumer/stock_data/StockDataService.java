package com.tradestream.market_data_consumer.stock_data;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class StockDataService {

    private final StockDataRepository repository;

    public StockDataService(StockDataRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void processMarketEvent(MarketDataEvent event) {
        LocalDate date = event.date();
        String ticker = event.ticker();
        BigDecimal price = event.price();
        long volume = event.volume();

        Optional<StockData> maybe = repository.findByTickerAndDate(ticker, date);

        StockData data = maybe.orElseGet(() ->
            StockData.builder()
                .id(UUID.randomUUID())
                .ticker(ticker)
                .name(event.name())
                .date(date)
                .open(price)
                .high(price)
                .low(price)
                .close(price)
                .volume(volume)
                .build()
        );

        if (maybe.isPresent()) {
            data.setHigh(data.getHigh().max(price));
            data.setLow(data.getLow().min(price));
            data.setClose(price);
            data.setVolume(data.getVolume() + volume);
        }

        repository.save(data);
    }

    public List<StockData> getAllLatestStocks() {
        return repository.findAllLatestStocks();
    }

    public Optional<StockData> getLatestByTicker(String ticker) {
        return repository.findTopByTickerOrderByDateDesc(ticker);
    }

    public List<StockDataDto> getAllLatestStockDtos() {
        return StockDataMapper.toDtoList(getAllLatestStocks());
    }

    public Optional<StockDataDto> getLatestStockDtoByTicker(String ticker) {
        return getLatestByTicker(ticker)
                .map(StockDataMapper::toDto);
    }

}


