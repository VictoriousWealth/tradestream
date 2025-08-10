package com.tradestream.orders_service.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.tradestream.orders_service.events.OrderPlaced;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OrderProducer {

    private final KafkaTemplate<String, Object> kafka;

    @Value("${KAFKA_TOPIC_ORDER_PLACED:order.placed.v1}")
    private String topic;

    public void publish(OrderPlaced evt) {
        // key by ticker to keep same symbol in the same partition
        kafka.send(topic, evt.ticker(), evt);
    }
}
