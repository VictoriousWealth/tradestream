package com.tradestream.market_data_consumer.stock_data;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for exposing stock data to other services (e.g., transaction-processor).
 * Only includes fields that are relevant for processing/validation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockDataDto {
    private String ticker;
    private String name;
    private BigDecimal close;
    private LocalDate date;
}
