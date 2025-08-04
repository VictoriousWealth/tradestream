package com.tradestream.market_data_consumer.stock_data;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StockDataRepository extends JpaRepository<StockData, UUID> {
    Optional<StockData> findByTickerAndDate(String ticker, LocalDate date);
}
