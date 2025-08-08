package com.tradestream.market_data_consumer.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

public record TradeExecuted(
    UUID tradeId,
    UUID orderId,
    UUID userId,
    String ticker,
    Double price,
    Integer quantity,
    String side,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant timestamp // ISO-8601 UTC
) {}
