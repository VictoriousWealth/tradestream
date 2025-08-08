package com.tradestream.transaction_processor.transaction;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TransactionRequest {

    @NotBlank
    @Size(min = 1, max = 10)
    private String ticker;

    @Positive
    private int quantity;

    @Positive
    private BigDecimal price;

    @NotNull
    private TransactionType type;

    public enum TransactionType {
        BUY,
        SELL
    }
}
