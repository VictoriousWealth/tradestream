// MatchingEngineApplication.java (add a runner)
package com.tradestream.matching_engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.tradestream.matching_engine.matching.MatchingService;
import com.tradestream.matching_engine.persistence.RestingOrderRepository;

@SpringBootApplication
public class MatchingEngineApplication {
    public static void main(String[] args) { SpringApplication.run(MatchingEngineApplication.class, args); }

    @Bean
    public org.springframework.boot.CommandLineRunner warmStart(RestingOrderRepository repo, MatchingService svc) {
        return args -> svc.loadActiveOrders(repo.findAllActive());
    }
}
