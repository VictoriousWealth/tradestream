package com.tradestream.portfolio_service.web;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import com.tradestream.portfolio_service.domain.Position;
import com.tradestream.portfolio_service.persistence.PositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/portfolio")
public class PortfolioController {

  private final PositionRepository repo;

  @GetMapping("/{userId}/positions")
  public List<Map<String, Object>> positions(@PathVariable UUID userId) {
    return repo.findByUserId(userId).stream().map(this::toView).collect(Collectors.toList());
  }

  @GetMapping("/{userId}/positions/{ticker}")
  public ResponseEntity<Map<String, Object>> position(@PathVariable UUID userId,
                                                      @PathVariable String ticker) {
    return repo.findById(new com.tradestream.portfolio_service.domain.PositionId(userId, ticker))
               .map(this::toView)
               .map(ResponseEntity::ok)
               .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/{userId}/summary")
  public Map<String, Object> summary(@PathVariable UUID userId) {
    var list = repo.findByUserId(userId);
    BigDecimal realized = list.stream().map(p -> nz(p.getRealizedPnl())).reduce(BigDecimal.ZERO, BigDecimal::add);
    // Unrealized requires a price feed; skip for MVP or keep null.
    Map<String,Object> out = new LinkedHashMap<>();
    out.put("realizedPnl", realized);
    out.put("unrealizedPnl", null);
    out.put("marketValue", null);
    out.put("totalPnl", realized);
    return out;
  }

  private Map<String,Object> toView(Position p) {
    Map<String,Object> m = new LinkedHashMap<>();
    m.put("ticker", p.getTicker());
    m.put("quantity", p.getQuantity());
    m.put("avgCost", p.getAvgCost());
    m.put("realizedPnl", p.getRealizedPnl());
    m.put("lastPrice", null);
    m.put("unrealizedPnl", null);
    m.put("updatedAt", p.getUpdatedAt());
    return m;
  }

  private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
