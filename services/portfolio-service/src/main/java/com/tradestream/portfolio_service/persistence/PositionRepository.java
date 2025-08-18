package com.tradestream.portfolio_service.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.tradestream.portfolio_service.domain.Position;
import com.tradestream.portfolio_service.domain.PositionId;

import jakarta.persistence.LockModeType;

@Repository
public interface PositionRepository extends JpaRepository<Position, PositionId> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Position p where p.userId=:userId and p.ticker=:ticker")
  Optional<Position> lockByUserAndTicker(@Param("userId") UUID userId,
                                         @Param("ticker") String ticker);

  List<Position> findByUserId(UUID userId);
}
