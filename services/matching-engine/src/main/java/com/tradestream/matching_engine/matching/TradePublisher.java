// matching/TradePublisher.java
package com.tradestream.matching_engine.matching;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.tradestream.matching_engine.config.EngineProps;
import com.tradestream.matching_engine.dto.TradeExecutedEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TradePublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final EngineProps props;

    public void publish(TradeExecutedEvent event, String key) {
        kafkaTemplate.send(props.getTradeExecuted(), key, event);
    }
}
