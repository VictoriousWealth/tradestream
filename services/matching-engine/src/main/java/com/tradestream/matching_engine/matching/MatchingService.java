// src/main/java/com/tradestream/matching_engine/matching/MatchingService.java
package com.tradestream.matching_engine.matching;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    private final RestingOrderRepository restingRepo;
    private final TradePublisher tradePublisher;

    // ticker -> order book (in-memory)
    private final Map<String, OrderBook> books = new ConcurrentHashMap<>();

    /** Warm-start: call on @PostConstruct from a runner */
    public void loadActiveOrders(List<RestingOrder> active) {
        active.forEach(ro -> books.computeIfAbsent(ro.getTicker(), t -> new OrderBook()).add(ro));
    }

    @Transactional
    public void cancel(UUID orderId) {
        restingRepo.findById(orderId).ifPresent(ro -> {
            ro.setStatus("CANCELED");
            restingRepo.save(ro);
            books.computeIfAbsent(ro.getTicker(), t -> new OrderBook()).remove(ro);
        });
    }

    /**
     * Core matching for an incoming order (from OrderPlaced).
     * Returns true if fully filled; false if anything rests/cancels.
     */
    @Transactional
    public boolean handleIncoming(OrderPlacedEvent evt) {
        RestingOrder incoming = toResting(evt);
        OrderBook book = books.computeIfAbsent(incoming.getTicker(), t -> new OrderBook());

        // FOK pre-check: must be fully fillable now
        if (incoming.getTimeInForce() == TimeInForce.FOK && !canFullyFill(incoming, book)) {
            return false; // reject without persisting
        }

        // Aggressively match while crossed
        while (incoming.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0 &&
               book.isCrossed(incoming.getPrice(), incoming.getSide())) {
            RestingOrder top = book.pollOpp(incoming.getSide());
            if (top == null) break;

            BigDecimal tradeQty = incoming.getRemainingQuantity().min(top.getRemainingQuantity());
            BigDecimal tradePrice = top.getPrice() != null ? top.getPrice() : incoming.getPrice(); // market/limit cross

            publishTrade(incoming, top, tradeQty, tradePrice);

            // update resting (from book)
            top.setRemainingQuantity(top.getRemainingQuantity().subtract(tradeQty));
            top.setStatus(top.getRemainingQuantity().signum() == 0 ? "FILLED" : "PARTIALLY_FILLED");
            restingRepo.save(top);
            if (top.getRemainingQuantity().signum() > 0) {
                book.requeueOpp(top); // still has qty
            }

            // update incoming
            incoming.setRemainingQuantity(incoming.getRemainingQuantity().subtract(tradeQty));
        }

        // Post-match resolution by TIF
        if (incoming.getRemainingQuantity().signum() == 0) {
            // fully filled, nothing to persist
            return true;
        }

        if (incoming.getTimeInForce() == TimeInForce.IOC) {
            // cancel remainder
            return false;
        }

        // Only LIMIT orders can rest; MARKET remainder cancels
        if (incoming.getOrderType() == OrderType.MARKET) {
            return false;
        }

        // Safety guard: LIMIT must have price before resting
        if (incoming.getPrice() == null) {
            throw new IllegalStateException("LIMIT order must have price to rest");
        }

        incoming.setStatus(incoming.getRemainingQuantity().compareTo(incoming.getOriginalQuantity()) < 0
                ? "PARTIALLY_FILLED" : "ACTIVE");
        restingRepo.save(incoming);
        book.add(incoming);
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
