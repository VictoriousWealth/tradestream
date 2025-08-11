// src/main/java/com/tradestream/matching_engine/matching/OrderBook.java
package com.tradestream.matching_engine.matching;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Optional;
import java.util.PriorityQueue;

import com.tradestream.matching_engine.domain.OrderSide;
import com.tradestream.matching_engine.domain.RestingOrder;

/**
 * One OrderBook per ticker.
 * BUY = max price first; SELL = min price first; within same price -> FIFO by createdAt.
 * Note: Only LIMIT orders are ever stored in the book; MARKET orders never rest.
 */
public class OrderBook {
    private final PriorityQueue<RestingOrder> bids;
    private final PriorityQueue<RestingOrder> asks;

    public OrderBook() {
        Comparator<RestingOrder> bidCmp = Comparator
                .comparing(OrderBook::priceOrThrow)      // highest first
                .reversed()
                .thenComparing(RestingOrder::getCreatedAt);
        Comparator<RestingOrder> askCmp = Comparator
                .comparing(OrderBook::priceOrThrow)      // lowest first
                .thenComparing(RestingOrder::getCreatedAt);
        this.bids = new PriorityQueue<>(bidCmp);
        this.asks = new PriorityQueue<>(askCmp);
    }

    private static BigDecimal priceOrThrow(RestingOrder r) {
        BigDecimal p = r.getPrice();
        if (p == null) {
            // We should never have null prices in the resting book.
            throw new IllegalStateException("Resting order must have a price (LIMIT only)");
        }
        return p;
    }

    public PriorityQueue<RestingOrder> side(OrderSide s) { return s == OrderSide.BUY ? bids : asks; }
    public PriorityQueue<RestingOrder> opp(OrderSide s)  { return s == OrderSide.BUY ? asks : bids; }

    public void add(RestingOrder ro) { side(ro.getSide()).add(ro); }
    public void remove(RestingOrder ro) { side(ro.getSide()).remove(ro); }

    public Optional<RestingOrder> peekBest(OrderSide aggressingSide) {
        return Optional.ofNullable(opp(aggressingSide).peek());
    }

    /**
     * Returns true if the aggressing price crosses the current top of opposite side.
     * MARKET (null aggressingPrice) crosses if opposite has any liquidity.
     */
    public boolean isCrossed(BigDecimal aggressingPrice, OrderSide aggressingSide) {
        RestingOrder top = opp(aggressingSide).peek();
        if (top == null) return false;
        BigDecimal topPrice = priceOrThrow(top);
        if (aggressingPrice == null) return true; // MARKET crosses if opposite exists
        return aggressingSide == OrderSide.BUY
                ? aggressingPrice.compareTo(topPrice) >= 0
                : aggressingPrice.compareTo(topPrice) <= 0;
    }

    public RestingOrder pollOpp(OrderSide aggressingSide) { return opp(aggressingSide).poll(); }
    public void requeueOpp(RestingOrder ro) {
        // 'ro' is from the opposite side relative to the aggressing order that matched it.
        opp(ro.getSide() == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY).add(ro);
    }
}
