package com.tradestream.orders_service.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tradestream.orders_service.domain.Order;
import com.tradestream.orders_service.domain.OrderStatus;
import com.tradestream.orders_service.domain.OrderType;
import com.tradestream.orders_service.dto.OrderResponse;
import com.tradestream.orders_service.dto.PlaceOrderRequest;
import com.tradestream.orders_service.events.OrderCancelledEvent;
import com.tradestream.orders_service.events.OrderPlaced;
import com.tradestream.orders_service.kafka.OrderProducer;
import com.tradestream.orders_service.repo.OrderRepository;

import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository repo;
    private final OrderProducer producer;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Value("${tradestream.topics.orderCancelled:order.cancelled.v1}")
    private String orderCancelledTopic;

    @Transactional
    public OrderResponse place(PlaceOrderRequest r) {
        // business validation
        if (r.type() == OrderType.LIMIT && r.price() == null) {
            throw new ValidationException("price is required for LIMIT orders");
        }
        if (r.type() == OrderType.MARKET && r.price() != null) {
            throw new ValidationException("price must be null for MARKET orders");
        }

        var order = Order.builder()
                .userId(r.userId())
                .ticker(r.ticker())
                .side(r.side())
                .type(r.type())
                .timeInForce(r.timeInForce())
                .quantity(r.quantity())
                .price(r.price())
                .status(OrderStatus.NEW)
                .build();

        order = repo.save(order);

        // publish event for matching-engine
        var evt = new OrderPlaced(
                order.getId(),
                order.getUserId(),
                order.getTicker(),
                order.getSide(),
                order.getType(),
                order.getTimeInForce(),
                order.getQuantity(),
                order.getPrice(),
                order.getCreatedAt()
        );
        producer.publish(evt);

        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(java.util.UUID id) {
        var o = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("order not found"));
        return toResponse(o);
    }

    private static OrderResponse toResponse(Order o) {
        return new OrderResponse(
            o.getId(),
            o.getUserId(),
            o.getTicker(),
            o.getSide(),
            o.getType(),
            o.getTimeInForce(),
            o.getQuantity(),
            o.getPrice(),
            o.getStatus(),
            o.getFilledQuantity(),
            o.remainingQuantity(),
            o.getLastFillPrice(),
            o.getCreatedAt(),
            o.getUpdatedAt()
        );
    }

    @Transactional
    public Order cancelOrder(UUID orderId) {
        Order order = repo.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (order.getStatus() == OrderStatus.CANCELED) {
            // Already cancelled → no Kafka event, just return
            return order;
        }

        if (order.getStatus() != OrderStatus.NEW) {
            throw new IllegalStateException("Only NEW orders can be cancelled");
        }

        order.setStatus(OrderStatus.CANCELED);
        order.setUpdatedAt(Instant.now());
        Order saved = repo.save(order);

        // Emit Kafka event once
        OrderCancelledEvent event = OrderCancelledEvent.builder()
                .orderId(saved.getId())
                .userId(saved.getUserId())
                .ticker(saved.getTicker())
                .quantity(saved.getQuantity())
                .price(saved.getPrice())
                .timestamp(saved.getUpdatedAt())
                .build();

        kafkaTemplate.send(orderCancelledTopic, saved.getTicker(), event);


        return saved;
    }

}
