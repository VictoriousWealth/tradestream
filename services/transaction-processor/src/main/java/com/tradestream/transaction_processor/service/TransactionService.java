package com.tradestream.transaction_processor.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.tradestream.transaction_processor.client.OrdersServiceClient;
import com.tradestream.transaction_processor.consumer.TradeExecutedEvent;
import com.tradestream.transaction_processor.domain.ProcessedMessage;
import com.tradestream.transaction_processor.domain.Transaction;
import com.tradestream.transaction_processor.producer.TransactionRecordedEvent;
import com.tradestream.transaction_processor.producer.TransactionRecordedProducer;
import com.tradestream.transaction_processor.repo.ProcessedMessageRepository;
import com.tradestream.transaction_processor.repo.TransactionRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final OrdersServiceClient ordersServiceClient;
    private final TransactionRecordedProducer recordedProducer;

    @Transactional
    public void processTrade(String topic, String messageKey, TradeExecutedEvent event) {
        // Idempotency check
        var key = new ProcessedMessage.Key(topic, event.getTradeId().toString());
        if (processedMessageRepository.existsById(key)) {
            return; // already processed
        }

        // Resolve buyer & seller user IDs from Orders Service
        UUID buyerUserId = ordersServiceClient.getUserIdForOrder(event.getBuyOrderId());
        UUID sellerUserId = ordersServiceClient.getUserIdForOrder(event.getSellOrderId());

        // Persist buyer transaction
        Transaction buyerTx = transactionRepository.save(Transaction.builder()
                .tradeId(event.getTradeId())
                .orderId(event.getBuyOrderId())
                .userId(buyerUserId)
                .side(Transaction.Side.BUY)
                .ticker(event.getTicker())
                .quantity(event.getQuantity())
                .price(event.getPrice())
                .executedAt(event.getTimestamp())
                .build());

        // Persist seller transaction
        Transaction sellerTx = transactionRepository.save(Transaction.builder()
                .tradeId(event.getTradeId())
                .orderId(event.getSellOrderId())
                .userId(sellerUserId)
                .side(Transaction.Side.SELL)
                .ticker(event.getTicker())
                .quantity(event.getQuantity())
                .price(event.getPrice())
                .executedAt(event.getTimestamp())
                .build());

        // Mark message as processed (idempotency)
        processedMessageRepository.save(new ProcessedMessage(key, Instant.now()));

        // Emit one event per journal row (post-persist; still within the same txn).
        // NOTE: In a perfect world, use the Outbox pattern to ensure "commit+publish" atomicity.
        // For MVP, idempotent consumers will handle rare duplicates.
        recordedProducer.publish(toEvent(buyerTx));
        recordedProducer.publish(toEvent(sellerTx));
    }

    private TransactionRecordedEvent toEvent(Transaction tx) {
        return TransactionRecordedEvent.builder()
                .eventId(UUID.randomUUID())
                .tradeId(tx.getTradeId())
                .orderId(tx.getOrderId())
                .userId(tx.getUserId())
                .side(tx.getSide().name())
                .ticker(tx.getTicker())
                .quantity(tx.getQuantity())
                .price(tx.getPrice())
                .executedAt(tx.getExecutedAt())
                .version(1)
                .build();
    }
}
