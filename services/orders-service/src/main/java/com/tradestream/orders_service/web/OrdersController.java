package com.tradestream.orders_service.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.tradestream.orders_service.domain.Order;
import com.tradestream.orders_service.dto.OrderResponse;
import com.tradestream.orders_service.dto.PlaceOrderRequest;
import com.tradestream.orders_service.service.OrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrdersController {

    private final OrderService service;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OrderResponse place(@Valid @RequestBody PlaceOrderRequest req) {
        return service.place(req);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Order> cancelOrder(@PathVariable UUID id) {
        Order cancelled = service.cancelOrder(id);
        return ResponseEntity.ok(cancelled);
    }
}
