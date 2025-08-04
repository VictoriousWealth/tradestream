package com.tradestream.market_data_consumer.stock_data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MarketDataEvent(

    @NotNull
    @Size(min = 1, max = 10)
    String ticker,

    @NotNull
    @Size(min = 1, max = 255)
    String name,

    @NotNull
    @Positive
    BigDecimal price,

    @Positive
    long volume,

    @NotNull
    LocalDate date

) {}
