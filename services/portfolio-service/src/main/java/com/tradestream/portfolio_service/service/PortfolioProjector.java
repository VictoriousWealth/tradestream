package com.tradestream.portfolio_service.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tradestream.portfolio_service.domain.Position;
import com.tradestream.portfolio_service.domain.ProcessedMessage;
import com.tradestream.portfolio_service.dto.TransactionRecordedEvent;
import com.tradestream.portfolio_service.persistence.PositionRepository;
import com.tradestream.portfolio_service.persistence.ProcessedMessageRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PortfolioProjector {

  private final PositionRepository posRepo;
  private final ProcessedMessageRepository msgRepo;

  @Transactional
  public void apply(String topic, UUID messageId, TransactionRecordedEvent e) {
    if (messageId == null) throw new IllegalArgumentException("eventId missing");
    if (msgRepo.existsByTopicAndMessageId(topic, messageId)) return;

    var userId = e.getUserId();
    var ticker = e.getTicker();
    var price  = nz(e.getPrice());
    var qtyEvt = nz(e.getQuantity());

    var existing = posRepo.lockByUserAndTicker(userId, ticker)
        .orElse(Position.builder()
            .userId(userId)
            .ticker(ticker)
            .quantity(BigDecimal.ZERO)
            .avgCost(null)
            .realizedPnl(BigDecimal.ZERO)
            .build());

    BigDecimal qty    = existing.getQuantity();
    BigDecimal avg    = existing.getAvgCost();
    BigDecimal rPnl   = existing.getRealizedPnl();

    switch (e.getSide()) {
      case "BUY" -> {
        BigDecimal newQty = qty.add(qtyEvt);
        BigDecimal base = (avg == null ? BigDecimal.ZERO : qty.multiply(avg));
        BigDecimal newAvg = newQty.signum() == 0 ? null : base.add(qtyEvt.multiply(price)).divide(newQty, 8, BigDecimal.ROUND_HALF_UP);
        existing.setQuantity(newQty);
        existing.setAvgCost(newAvg);
      }
      case "SELL" -> {
        BigDecimal sellQty = qtyEvt.min(qty.max(BigDecimal.ZERO)); // clamp to current long qty
        if (sellQty.signum() > 0 && avg != null) {
          rPnl = rPnl.add(price.subtract(avg).multiply(sellQty));
        }
        existing.setQuantity(qty.subtract(sellQty));
        if (existing.getQuantity().signum() == 0) existing.setAvgCost(null);
        existing.setRealizedPnl(rPnl);
      }
      default -> throw new IllegalArgumentException("Unknown side: " + e.getSide());
    }

    posRepo.save(existing);

    msgRepo.save(ProcessedMessage.builder()
        .topic(topic)
        .messageId(messageId)
        .receivedAt(OffsetDateTime.now())
        .build());
  }

  private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
