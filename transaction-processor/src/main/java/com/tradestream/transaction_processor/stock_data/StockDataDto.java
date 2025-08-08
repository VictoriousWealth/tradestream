package com.tradestream.transaction_processor.stock_data;


import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

@Data
public class StockDataDto {
    private String ticker;
    private String name;
    private BigDecimal close;
    private LocalDate date;
}
