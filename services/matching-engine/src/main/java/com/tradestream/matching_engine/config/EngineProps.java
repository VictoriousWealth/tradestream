// config/EngineProps.java
package com.tradestream.matching_engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "tradestream.topics")
public class EngineProps {
    private String orderPlaced;
    private String orderCancelled;
    private String tradeExecuted;
    // getters/setters
    public String getOrderPlaced() { return orderPlaced; }
    public void setOrderPlaced(String v) { orderPlaced = v; }
    public String getOrderCancelled() { return orderCancelled; }
    public void setOrderCancelled(String v) { orderCancelled = v; }
    public String getTradeExecuted() { return tradeExecuted; }
    public void setTradeExecuted(String v) { tradeExecuted = v; }
}
