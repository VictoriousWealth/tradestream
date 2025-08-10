package com.tradestream.orders_service.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.tradestream.orders_service.domain.OrderType;
import com.tradestream.orders_service.domain.Side;
import com.tradestream.orders_service.domain.TimeInForce;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PlaceOrderRequest(
        @NotNull UUID userId,
        @NotBlank @Size(max = 16) String ticker,
        @NotNull Side side,
        @NotNull OrderType type,
        @NotNull TimeInForce timeInForce,
        @NotNull @DecimalMin(value = "0.000001") BigDecimal quantity,
        @DecimalMin(value = "0.000001") BigDecimal price // required if type=LIMIT
) { }
