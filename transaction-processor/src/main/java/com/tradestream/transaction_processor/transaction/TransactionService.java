package com.tradestream.transaction_processor.transaction;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import com.tradestream.transaction_processor.market_data.MarketDataClient;
import com.tradestream.transaction_processor.portofolio.Portfolio;
import com.tradestream.transaction_processor.portofolio.PortfolioRepository;
import com.tradestream.transaction_processor.stock_data.StockDataDto;
import com.tradestream.transaction_processor.transaction.Transaction.TransactionType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final PortfolioRepository portfolioRepository;
    private final MarketDataClient marketDataClient;

    public TransactionService(TransactionRepository transactionRepository, PortfolioRepository portfolioRepository, MarketDataClient marketDataClient) {
        this.transactionRepository = transactionRepository;
        this.portfolioRepository = portfolioRepository;
        this.marketDataClient = marketDataClient;
    }

    @Transactional
    public void processTransaction(UUID userId, String ticker, int quantity, BigDecimal price, TransactionType type) {
        
        Optional<StockDataDto> stockOpt = marketDataClient.getStockByTicker(ticker);

        if (stockOpt.isEmpty()) {
            throw new IllegalArgumentException("Invalid ticker: " + ticker);
        }

        StockDataDto stock = stockOpt.get();


        if (type == TransactionType.SELL && quantity <= 0) {
            throw new IllegalArgumentException("Sell quantity must be positive");
        }
        if (type == TransactionType.BUY && quantity <= 0) {
            throw new IllegalArgumentException("Buy quantity must be positive");
        }

        // Convert to signed quantity
        int signedQuantity = type == TransactionType.SELL ? -quantity : quantity;

        // Fetch existing portfolio entry
        Portfolio portfolio = portfolioRepository.findByUserIdAndTicker(userId, ticker)
                .orElseGet(() -> Portfolio.builder()
                        .userId(userId)
                        .ticker(ticker)
                        .quantity(0)
                        .build());

        int newQuantity = portfolio.getQuantity() + signedQuantity;

        if (newQuantity < 0) {
            throw new IllegalStateException("Insufficient shares to sell");
        }

        portfolio.setQuantity(newQuantity);
        portfolioRepository.save(portfolio);

        Transaction tx = Transaction.builder()
                .userId(userId)
                .ticker(ticker)
                .quantity(signedQuantity)
                .price(price)
                .type(type)
                .build();

        transactionRepository.save(tx);

        try {
            marketDataClient.publishMarketEvent(
                    ticker,
                    stock.getName(),                 
                    java.time.LocalDate.now(),
                    price,
                    Math.abs((long) quantity)        
            );
        } catch (Exception e) {
            // Don't fail the portfolio update just because telemetry failed
            // Consider logging with a proper logger
            System.err.println("Failed to publish market event: " + e.getMessage());
        }
        }

    public List<Portfolio> getPortfolioForUser(UUID userId) {
        return portfolioRepository.findByUserId(userId);
    }
    
    public List<Transaction> getTransactionHistory(UUID userId) {
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

}
