package com.tradestream.transaction_processor.client;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import lombok.Data;

@Component
public class OrdersServiceClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public OrdersServiceClient(RestTemplate restTemplate,
                               @Value("${orders.baseUrl}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public UUID getUserIdForOrder(UUID orderId) {
        // Assuming Orders Service has an endpoint like /orders/{id}
        OrderDto dto = restTemplate.getForObject(baseUrl + "/orders/" + orderId, OrderDto.class);
        if (dto == null || dto.getUserId() == null) {
            throw new IllegalStateException("Could not resolve user for order " + orderId);
        }
        return dto.getUserId();
    }

    @Data
    private static class OrderDto {
        private UUID id;
        private UUID userId;
    }
}
