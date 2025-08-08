package com.tradestream.market_data_consumer.stock_data;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StockDataRepository extends JpaRepository<StockData, UUID> {
    Optional<StockData> findByTickerAndDate(String ticker, LocalDate date);

    @Query("""
        SELECT s FROM StockData s
        WHERE (s.ticker, s.date) IN (
            SELECT s2.ticker, MAX(s2.date)
            FROM StockData s2
            GROUP BY s2.ticker
        )
    """)
    List<StockData> findAllLatestStocks();

    Optional<StockData> findTopByTickerOrderByDateDesc(String ticker);

}
