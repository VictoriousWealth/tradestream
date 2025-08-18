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
        var key = new ProcessedMessage.Key(topic, event.getTradeId().toString());
        if (processedMessageRepository.existsById(key)) {
            return;
        }

        var buyerUserId  = ordersServiceClient.getUserIdForOrder(event.getBuyOrderId());
        var sellerUserId = ordersServiceClient.getUserIdForOrder(event.getSellOrderId());

        var buyerTx = transactionRepository.save(Transaction.builder()
                .tradeId(event.getTradeId())
                .orderId(event.getBuyOrderId())
                .userId(buyerUserId)
                .side(Transaction.Side.BUY)
                .ticker(event.getTicker())
                .quantity(event.getQuantity()) // BigDecimal
                .price(event.getPrice())
                .executedAt(event.getTimestamp())
                .build());

        var sellerTx = transactionRepository.save(Transaction.builder()
                .tradeId(event.getTradeId())
                .orderId(event.getSellOrderId())
                .userId(sellerUserId)
                .side(Transaction.Side.SELL)
                .ticker(event.getTicker())
                .quantity(event.getQuantity()) // BigDecimal
                .price(event.getPrice())
                .executedAt(event.getTimestamp())
                .build());

        processedMessageRepository.save(new ProcessedMessage(key, Instant.now()));

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
