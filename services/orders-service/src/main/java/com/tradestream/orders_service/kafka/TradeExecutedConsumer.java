package com.tradestream.orders_service.kafka;

import java.math.BigDecimal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.tradestream.orders_service.domain.OrderStatus;
import com.tradestream.orders_service.dto.TradeExecuted;
import com.tradestream.orders_service.repo.IngestedFillRepository;
import com.tradestream.orders_service.repo.OrderRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TradeExecutedConsumer {

  private static final Logger log = LoggerFactory.getLogger(TradeExecutedConsumer.class);

  private final IngestedFillRepository ingestedRepo;
  private final OrderRepository orderRepo;

  @KafkaListener(
      topics = "${tradestream.topics.tradeExecuted:trade.executed.v1}",
      groupId = "${KAFKA_CONSUMER_GROUP:orders-exec-consumer}"
  )
  @Transactional
  public void onExecuted(TradeExecuted t) {
    boolean touched = false;

    if (t.buyOrderId() != null) {
      touched |= applyOnce(t.tradeId(), t.buyOrderId(), t.ticker(), t.timestamp(), t.quantity(), t.price());
    }
    if (t.sellOrderId() != null) {
      touched |= applyOnce(t.tradeId(), t.sellOrderId(), t.ticker(), t.timestamp(), t.quantity(), t.price());
    }

    if (!touched) {
      log.debug("Trade {} already applied for both legs; ignoring", t.tradeId());
    }
  }

  private boolean applyOnce(UUID tradeId, UUID orderId, String ticker,
                            java.time.Instant ts, BigDecimal qty, BigDecimal price) {
    int inserted = ingestedRepo.tryInsert(orderId, tradeId, ticker, ts);
    if (inserted == 0) return false; // already processed this (order,trade)

    return orderRepo.findByIdForUpdate(orderId).map(order -> {
      if (order.getStatus() == OrderStatus.CANCELED || order.getStatus() == OrderStatus.FILLED) return true;

      BigDecimal remaining = order.remainingQuantity();
      if (remaining.signum() <= 0) return true;

      BigDecimal exec = qty.compareTo(remaining) > 0 ? remaining : qty;
      order.applyFill(exec);
      order.setLastFillPrice(price);
      order.setUpdatedAt(java.time.Instant.now());
      orderRepo.save(order);

      log.info("Applied fill {} to order {} → status={}, filled={}, remaining={}",
          exec, order.getId(), order.getStatus(), order.getFilledQuantity(), order.remainingQuantity());
      return true;
    }).orElseGet(() -> {
      log.warn("Trade {} references unknown order {}", tradeId, orderId);
      return true; // we inserted the idempotency row; treat as handled
    });
  }
}
