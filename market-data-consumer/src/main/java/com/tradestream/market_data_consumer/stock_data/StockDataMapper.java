package com.tradestream.market_data_consumer.stock_data;

import java.util.List;
import java.util.stream.Collectors;

public class StockDataMapper {

    public static StockDataDto toDto(StockData stock) {
        return new StockDataDto(
            stock.getTicker(),
            stock.getName(),
            stock.getClose(),
            stock.getDate()
        );
    }

    public static List<StockDataDto> toDtoList(List<StockData> stocks) {
        return stocks.stream()
                .map(StockDataMapper::toDto)
                .collect(Collectors.toList());
    }
}
