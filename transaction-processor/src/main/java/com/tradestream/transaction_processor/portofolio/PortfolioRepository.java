package com.tradestream.transaction_processor.portofolio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {

    Optional<Portfolio> findByUserIdAndTicker(UUID userId, String ticker);

    // Optionally, if you want to fetch all holdings for a user
    List<Portfolio> findByUserId(UUID userId);
}
