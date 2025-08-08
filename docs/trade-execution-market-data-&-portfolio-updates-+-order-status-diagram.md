# Trade Execution → Market Data & Portfolio Updates (+ Order Status)

```mermaid 
sequenceDiagram
    autonumber
    participant B as Event Bus (Kafka/RabbitMQ)
    participant MD as Market Data Service
    participant CDB as Candles DB (OHLC+Volume)
    participant Q as Redis Cache (quotes/candles)
    participant PF as Portfolio Service
    participant PDB as Portfolio DB (positions)
    participant TXDB as Transactions DB (fills)
    participant O as Orders Service
    participant OBDB as Orders DB
    participant C as Client App

    Note over B: TradeExecuted{tradeId, orderId, userId, ticker, price, qty, side, ts}

    B-->>MD: TradeExecuted
    MD->>CDB: Upsert candle for ts.date(ticker):<br/>open(if first), high=max, low=min, close=price,<br/>volume += qty
    MD->>Q: Cache update (quotes/top-of-book/last trade) [TTL 1–5s]
    MD-->>B: (optional) Publish QuoteUpdated / BarUpdated

    B-->>PF: TradeExecuted
    PF->>PDB: Upsert position (qty, avg_cost) per userId+ticker
    PF->>TXDB: Append transaction history
    PF-->>B: (optional) Publish PortfolioUpdated

    B-->>O: TradeExecuted
    O->>OBDB: Update order (filled_qty += qty, status=PARTIALLY_FILLED/FILLED)
    OBDB-->>O: OK
    O-->>C: (if WebSocket/SSE) Push order status & fills
    Note over C: If polling, C hits GET /orders/{id} via Gateway
```

---
