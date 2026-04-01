package com.tradestream.orders_service.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.tradestream.orders_service.domain.OrderStatus;
import com.tradestream.orders_service.domain.OrderType;
import com.tradestream.orders_service.domain.Side;
import com.tradestream.orders_service.domain.TimeInForce;
import com.tradestream.orders_service.dto.OrderResponse;
import com.tradestream.orders_service.service.OrderService;

@WebMvcTest(controllers = OrdersController.class)
class OrdersControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @Test
    void placeReturnsBadRequestForInvalidPayload() throws Exception {
        String body = """
                {
                  "userId": "%s",
                  "ticker": "",
                  "side": "BUY",
                  "type": "LIMIT",
                  "timeInForce": "GTC",
                  "quantity": 10,
                  "price": 150.5
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("invalid parameter"));
    }

    @Test
    void placeReturnsAcceptedForValidRequest() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(orderService.place(any())).thenReturn(new OrderResponse(
                orderId,
                userId,
                "AAPL",
                Side.BUY,
                OrderType.LIMIT,
                TimeInForce.GTC,
                new BigDecimal("10"),
                new BigDecimal("150.50"),
                OrderStatus.NEW,
                BigDecimal.ZERO,
                new BigDecimal("10"),
                null,
                Instant.parse("2026-03-17T12:00:00Z"),
                Instant.parse("2026-03-17T12:00:00Z")
        ));

        String body = """
                {
                  "userId": "%s",
                  "ticker": "AAPL",
                  "side": "BUY",
                  "type": "LIMIT",
                  "timeInForce": "GTC",
                  "quantity": 10,
                  "price": 150.5
                }
                """.formatted(userId);

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("NEW"));
    }

    @Test
    void cancelReturnsConflictForInvalidStateTransition() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.cancelOrder(orderId)).thenThrow(new IllegalStateException("Only NEW orders can be cancelled"));

        mockMvc.perform(post("/orders/{id}/cancel", orderId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Only NEW orders can be cancelled"));
    }
}
