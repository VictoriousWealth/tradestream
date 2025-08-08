package com.tradestream.market_data_consumer.kafka;

import com.tradestream.market_data_consumer.dto.TradeExecuted;
import com.tradestream.market_data_consumer.service.AggregationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeExecutedListener {

    private final AggregationService aggregation;

    @KafkaListener(
        topics = "${tradestream.topics.tradeExecuted:trade.executed.v1}",
        groupId = "${spring.kafka.consumer.group-id:md-consumer}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onTrade(TradeExecuted evt) {
        boolean applied = aggregation.process(evt);
        if (applied) {
            log.debug("Aggregated trade {} for {} @ {}", evt.tradeId(), evt.ticker(), evt.timestamp());
        } else {
            log.debug("Duplicate trade skipped: {}", evt.tradeId());
        }
    }
}
