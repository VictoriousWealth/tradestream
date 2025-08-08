package com.tradestream.transaction_processor.market_data;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.tradestream.transaction_processor.stock_data.StockDataDto;

@Service
public class MarketDataClient {

    private final RestTemplate restTemplate;
    private final String marketDataBaseUrl;

    public MarketDataClient(RestTemplate restTemplate,
                            @Value("${market.service.url}") String marketDataBaseUrl) {
        this.restTemplate = restTemplate;
        this.marketDataBaseUrl = marketDataBaseUrl;
    }

    public Optional<StockDataDto> getStockByTicker(String ticker) {
        String url = marketDataBaseUrl + "/api/stock/" + ticker;
        try {
            ResponseEntity<StockDataDto> response = restTemplate.getForEntity(url, StockDataDto.class);
            return Optional.ofNullable(response.getBody());
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    public void publishMarketEvent(String ticker, String name, LocalDate date, java.math.BigDecimal price, long volume) {
        var url = marketDataBaseUrl + "/api/stock/event";
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var payload = MarketDataEvent.builder()
                .ticker(ticker)
                .name(name)
                .price(price)
                .volume(volume)
                .date(date)
                .build();
        restTemplate.postForEntity(url, new HttpEntity<>(payload, headers), Void.class);
    }
    
}
