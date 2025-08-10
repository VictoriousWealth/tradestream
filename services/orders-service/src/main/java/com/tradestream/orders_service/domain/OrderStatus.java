package com.tradestream.orders_service.domain;

public enum OrderStatus {
    NEW,         // accepted by orders-service, awaiting matching
    CANCELED,
    PARTIALLY_FILLED,
    FILLED,
    REJECTED,    // failed validation
    EXPIRED
}
