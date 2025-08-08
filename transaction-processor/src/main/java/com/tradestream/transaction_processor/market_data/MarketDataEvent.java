package com.tradestream.transaction_processor.market_data;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
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
