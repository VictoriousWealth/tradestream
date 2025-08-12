// src/main/java/com/tradestream/matching_engine/matching/MatchingService.java
package com.tradestream.matching_engine.matching;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tradestream.matching_engine.domain.OrderSide;
import com.tradestream.matching_engine.domain.OrderType;
import com.tradestream.matching_engine.domain.RestingOrder;
import com.tradestream.matching_engine.domain.TimeInForce;
import com.tradestream.matching_engine.dto.OrderPlacedEvent;
import com.tradestream.matching_engine.dto.TradeExecutedEvent;
import com.tradestream.matching_engine.persistence.RestingOrderRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MatchingService {
    private static final Logger log = LoggerFactory.getLogger(MatchingService.class);

    private final RestingOrderRepository restingRepo;
    private final TradePublisher tradePublisher;

    // ticker -> order book (in-memory)
    private final Map<String, OrderBook> books = new ConcurrentHashMap<>();

    public void loadActiveOrders(List<RestingOrder> active) {
        log.info("Loading {} active orders into in-memory books", active.size());
        active.forEach(ro -> {
            books.computeIfAbsent(ro.getTicker(), t -> new OrderBook()).add(ro);
            log.debug("Loaded order {} into book for {}", ro.getId(), ro.getTicker());
        });
    }

    @Transactional
    public void cancel(UUID orderId) {
        log.info("Received cancel for orderId={}", orderId);
        restingRepo.findById(orderId).ifPresentOrElse(ro -> {
            ro.setStatus("CANCELED");
            restingRepo.save(ro);
            books.computeIfAbsent(ro.getTicker(), t -> new OrderBook()).remove(ro);
            boolean removed = true;
            log.info("Cancel applied: id={} ticker={} removedFromBook={}", orderId, ro.getTicker(), removed);
        }, () -> log.warn("Cancel requested for unknown orderId={}", orderId));
    }

    @Transactional
    public boolean handleIncoming(OrderPlacedEvent evt) {
        log.info("Handling incoming order: {}", evt);
        RestingOrder incoming = toResting(evt);
        OrderBook book = books.computeIfAbsent(incoming.getTicker(), t -> new OrderBook());

        // FOK pre-check
        if (incoming.getTimeInForce() == TimeInForce.FOK && !canFullyFill(incoming, book)) {
            log.info("Rejecting FOK order {} - cannot fully fill", incoming.getId());
            return false;
        }

        // Matching loop
        while (incoming.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0 &&
               book.isCrossed(incoming.getPrice(), incoming.getSide())) {
            RestingOrder top = book.pollOpp(incoming.getSide());
            if (top == null) break;

            BigDecimal tradeQty = incoming.getRemainingQuantity().min(top.getRemainingQuantity());
            BigDecimal tradePrice = top.getPrice() != null ? top.getPrice() : incoming.getPrice();

            log.info("Match found: incoming={} resting={} qty={} price={}",
                    incoming.getId(), top.getId(), tradeQty, tradePrice);

            publishTrade(incoming, top, tradeQty, tradePrice);

            top.setRemainingQuantity(top.getRemainingQuantity().subtract(tradeQty));
            top.setStatus(top.getRemainingQuantity().signum() == 0 ? "FILLED" : "PARTIALLY_FILLED");
            restingRepo.save(top);
            if (top.getRemainingQuantity().signum() > 0) {
                book.requeueOpp(top);
                log.debug("Re-queued partially filled order {}", top.getId());
            }

            incoming.setRemainingQuantity(incoming.getRemainingQuantity().subtract(tradeQty));
        }

        // Post-match persistence
        if (incoming.getRemainingQuantity().signum() == 0) {
            log.info("Order {} fully filled", incoming.getId());
            return true;
        }

        if (incoming.getTimeInForce() == TimeInForce.IOC) {
            log.info("Order {} IOC - unfilled qty canceled", incoming.getId());
            return false;
        }

        if (incoming.getOrderType() == OrderType.MARKET) {
            log.info("Order {} MARKET - remainder canceled", incoming.getId());
            return false;
        }

        if (incoming.getPrice() == null) {
            throw new IllegalStateException("LIMIT order must have price to rest");
        }

        incoming.setStatus(incoming.getRemainingQuantity().compareTo(incoming.getOriginalQuantity()) < 0
                ? "PARTIALLY_FILLED" : "ACTIVE");
        restingRepo.save(incoming);
        book.add(incoming);
        log.info("Rested order {} with status {} and remaining qty {}",
                incoming.getId(), incoming.getStatus(), incoming.getRemainingQuantity());
        return false;
    }

    private void publishTrade(RestingOrder a, RestingOrder b, BigDecimal qty, BigDecimal price) {
        boolean aIsBuy = a.getSide() == OrderSide.BUY;
        UUID buyId = aIsBuy ? a.getId() : b.getId();
        UUID sellId = aIsBuy ? b.getId() : a.getId();

        TradeExecutedEvent trade = TradeExecutedEvent.builder()
                .tradeId(UUID.randomUUID())
                .buyOrderId(buyId)
                .sellOrderId(sellId)
                .ticker(a.getTicker())
                .price(price)
                .quantity(qty)
                .timestamp(OffsetDateTime.now().withNano(0))
                .build();

        tradePublisher.publish(trade, a.getTicker()); // key by ticker for downstream partitioning
    }

    private boolean crosses(BigDecimal incomingPrice, OrderSide side, BigDecimal restingPrice) {
        if (incomingPrice == null) return true; // MARKET crosses if opposite exists
        return side == OrderSide.BUY
                ? incomingPrice.compareTo(restingPrice) >= 0
                : incomingPrice.compareTo(restingPrice) <= 0;
    }

    /** True if the entire incoming quantity can be filled immediately at acceptable prices. */
    private boolean canFullyFill(RestingOrder incoming, OrderBook book) {
        BigDecimal need = incoming.getRemainingQuantity();
        BigDecimal have = BigDecimal.ZERO;

        var snapshot = new ArrayList<>(book.opp(incoming.getSide()));
        snapshot.sort(book.opp(incoming.getSide()).comparator()); // best-first

        for (RestingOrder ro : snapshot) {
            BigDecimal rp = ro.getPrice(); // non-null in book
            if (!crosses(incoming.getPrice(), incoming.getSide(), rp)) break;
            have = have.add(ro.getRemainingQuantity());
            if (have.compareTo(need) >= 0) return true;
        }
        return false;
    }

    private RestingOrder toResting(OrderPlacedEvent e) {
        OffsetDateTime now = OffsetDateTime.now();
        return RestingOrder.builder()
                .id(e.getOrderId())
                .userId(e.getUserId())
                .ticker(e.getTicker())
                .side(e.getSide())
                .orderType(e.getOrderType())
                .timeInForce(e.getTimeInForce())
                .price(e.getPrice())
                .originalQuantity(e.getQuantity())
                .remainingQuantity(e.getQuantity())
                .status("ACTIVE")
                .createdAt(now)
                .build();
    }
}
