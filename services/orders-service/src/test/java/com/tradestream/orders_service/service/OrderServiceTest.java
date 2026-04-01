package com.tradestream.orders_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.tradestream.orders_service.domain.Order;
import com.tradestream.orders_service.domain.OrderStatus;
import com.tradestream.orders_service.domain.OrderType;
import com.tradestream.orders_service.domain.Side;
import com.tradestream.orders_service.domain.TimeInForce;
import com.tradestream.orders_service.dto.OrderResponse;
import com.tradestream.orders_service.dto.PlaceOrderRequest;
import com.tradestream.orders_service.events.OrderCancelledEvent;
import com.tradestream.orders_service.events.OrderPlaced;
import com.tradestream.orders_service.kafka.OrderProducer;
import com.tradestream.orders_service.repo.OrderRepository;

import jakarta.validation.ValidationException;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository repo;

    @Mock
    private OrderProducer producer;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private OrderService service;

    @Captor
    private ArgumentCaptor<OrderPlaced> orderPlacedCaptor;

    @Captor
    private ArgumentCaptor<OrderCancelledEvent> orderCancelledCaptor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "orderCancelledTopic", "order.cancelled.v1");
    }

    @Test
    void placeRejectsLimitOrderWithoutPrice() {
        PlaceOrderRequest request = new PlaceOrderRequest(
                UUID.randomUUID(),
                "AAPL",
                Side.BUY,
                OrderType.LIMIT,
                TimeInForce.GTC,
                new BigDecimal("10"),
                null
        );

        assertThatThrownBy(() -> service.place(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("price is required for LIMIT orders");

        verify(repo, never()).save(any(Order.class));
        verify(producer, never()).publish(any(OrderPlaced.class));
    }

    @Test
    void placePublishesOrderPlacedEventForValidOrder() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-03-17T12:00:00Z");
        PlaceOrderRequest request = new PlaceOrderRequest(
                userId,
                "AAPL",
                Side.BUY,
                OrderType.LIMIT,
                TimeInForce.GTC,
                new BigDecimal("10"),
                new BigDecimal("150.50")
        );

        Order persisted = Order.builder()
                .id(orderId)
                .userId(userId)
                .ticker("AAPL")
                .side(Side.BUY)
                .type(OrderType.LIMIT)
                .timeInForce(TimeInForce.GTC)
                .quantity(new BigDecimal("10"))
                .price(new BigDecimal("150.50"))
                .status(OrderStatus.NEW)
                .filledQuantity(BigDecimal.ZERO)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();

        when(repo.save(any(Order.class))).thenReturn(persisted);

        OrderResponse response = service.place(request);

        assertThat(response.id()).isEqualTo(orderId);
        assertThat(response.status()).isEqualTo(OrderStatus.NEW);
        assertThat(response.remainingQuantity()).isEqualByComparingTo("10");
        verify(producer).publish(orderPlacedCaptor.capture());

        OrderPlaced published = orderPlacedCaptor.getValue();
        assertThat(published.orderId()).isEqualTo(orderId);
        assertThat(published.userId()).isEqualTo(userId);
        assertThat(published.ticker()).isEqualTo("AAPL");
        assertThat(published.price()).isEqualByComparingTo("150.50");
        assertThat(published.timestamp()).isEqualTo(createdAt);
    }

    @Test
    void cancelAlreadyCancelledOrderDoesNotEmitDuplicateEvent() {
        UUID orderId = UUID.randomUUID();
        Order existing = Order.builder()
                .id(orderId)
                .userId(UUID.randomUUID())
                .ticker("AAPL")
                .side(Side.SELL)
                .type(OrderType.LIMIT)
                .timeInForce(TimeInForce.GTC)
                .quantity(new BigDecimal("5"))
                .price(new BigDecimal("200"))
                .status(OrderStatus.CANCELED)
                .filledQuantity(BigDecimal.ZERO)
                .createdAt(Instant.parse("2026-03-17T12:00:00Z"))
                .updatedAt(Instant.parse("2026-03-17T12:05:00Z"))
                .build();

        when(repo.findById(orderId)).thenReturn(Optional.of(existing));

        Order result = service.cancelOrder(orderId);

        assertThat(result).isSameAs(existing);
        verify(repo, never()).save(any(Order.class));
        verify(kafkaTemplate, never()).send(any(String.class), any(String.class), any(OrderCancelledEvent.class));
    }

    @Test
    void cancelNewOrderPersistsStateAndPublishesCancelEvent() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Order existing = Order.builder()
                .id(orderId)
                .userId(userId)
                .ticker("MSFT")
                .side(Side.SELL)
                .type(OrderType.LIMIT)
                .timeInForce(TimeInForce.GTC)
                .quantity(new BigDecimal("5"))
                .price(new BigDecimal("200"))
                .status(OrderStatus.NEW)
                .filledQuantity(BigDecimal.ZERO)
                .createdAt(Instant.parse("2026-03-17T12:00:00Z"))
                .updatedAt(Instant.parse("2026-03-17T12:00:00Z"))
                .build();

        when(repo.findById(orderId)).thenReturn(Optional.of(existing));
        when(repo.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order result = service.cancelOrder(orderId);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELED);
        verify(kafkaTemplate).send(eq("order.cancelled.v1"), eq("MSFT"), orderCancelledCaptor.capture());

        OrderCancelledEvent event = orderCancelledCaptor.getValue();
        assertThat(event.getOrderId()).isEqualTo(orderId);
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getTicker()).isEqualTo("MSFT");
        assertThat(event.getQuantity()).isEqualByComparingTo("5");
    }
}
