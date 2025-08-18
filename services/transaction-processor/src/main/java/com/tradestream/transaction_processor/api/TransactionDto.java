package com.tradestream.transaction_processor.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.tradestream.transaction_processor.domain.Transaction;

public record TransactionDto(
        UUID id,
        UUID tradeId,
        UUID orderId,
        UUID userId,
        String side,
        String ticker,
        BigDecimal quantity,   // <—
        BigDecimal price,
        Instant executedAt
) {
    public static TransactionDto from(Transaction t) {
        return new TransactionDto(
                t.getId(), t.getTradeId(), t.getOrderId(), t.getUserId(),
                t.getSide().name(), t.getTicker(), t.getQuantity(), t.getPrice(), t.getExecutedAt()
        );
    }
}
