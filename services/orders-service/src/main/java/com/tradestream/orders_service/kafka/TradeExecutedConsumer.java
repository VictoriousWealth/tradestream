package com.tradestream.orders_service.kafka;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.tradestream.orders_service.domain.Order;
import com.tradestream.orders_service.domain.OrderStatus;
import com.tradestream.orders_service.dto.TradeExecuted;
import com.tradestream.orders_service.repo.IngestedTradeRepository;
import com.tradestream.orders_service.repo.OrderRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TradeExecutedConsumer {

    private static final Logger log = LoggerFactory.getLogger(TradeExecutedConsumer.class);

    private final IngestedTradeRepository ingestedRepo;
    private final OrderRepository orderRepo;

    @KafkaListener(
        topics = "${tradestream.topics.tradeExecuted:trade.executed.v1}",
        groupId = "${KAFKA_CONSUMER_GROUP:orders-exec-consumer}",
        containerFactory = "kafkaListenerContainerFactory" // your default is fine if configured
    )
    @Transactional
    public void onExecuted(TradeExecuted t) {
        // 1) Idempotency guard
        int inserted = ingestedRepo.tryInsert(t.tradeId(), t.orderId(), t.ticker(), t.timestamp());
        if (inserted == 0) {
            // already processed this tradeId
            log.debug("Duplicate trade {} ignored", t.tradeId());
            return;
        }

        // 2) Lock order row
        var opt = orderRepo.findByIdForUpdate(t.orderId());
        if (opt.isEmpty()) {
            log.warn("Trade {} references unknown order {}", t.tradeId(), t.orderId());
            return; // or throw
        }
        Order order = opt.get();

        // 3) Ignore if terminal in a way we don't want to fill (CANCELED or FILLED)
        if (order.getStatus() == OrderStatus.CANCELED || order.getStatus() == OrderStatus.FILLED) {
            log.warn("Trade {} arrived for terminal order {} with status {}", t.tradeId(), order.getId(), order.getStatus());
            return;
        }

        BigDecimal execQty = t.quantity();
        BigDecimal remaining = order.remainingQuantity();

        if (remaining.signum() <= 0) {
            // already fully filled due to a previous message
            log.debug("Order {} already fully filled; trade {} ignored", order.getId(), t.tradeId());
            return;
        }

        // Cap exec to remaining to avoid accidental overfill from bad events
        if (execQty.compareTo(remaining) > 0) {
            log.warn("Trade {} quantity {} > remaining {} for order {}, capping", t.tradeId(), execQty, remaining, order.getId());
            execQty = remaining;
        }

        // 4) Apply fill
        order.applyFill(execQty);
        order.setUpdatedAt(java.time.Instant.now());
        order.setLastFillPrice(t.price());

        orderRepo.save(order);
        log.info("Applied fill {} to order {} → status={}, filled={}, remaining={}",
                execQty, order.getId(), order.getStatus(), order.getFilledQuantity(), order.remainingQuantity());
    }
}
