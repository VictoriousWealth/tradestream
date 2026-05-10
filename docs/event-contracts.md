# Event Contracts (Current Implementation)

Last reviewed against code/config on 2026-05-10.

This document describes the actual Kafka/Redpanda contracts currently used by TradeStream. It is not a future-state schema registry spec. Where the implementation is inconsistent, that inconsistency is called out explicitly.

---

## Topic inventory

| Topic | Primary producer | Primary consumers | Key strategy in code | Notes |
| --- | --- | --- | --- | --- |
| `order.placed.v1` | `orders-service` | `matching-engine` | `ticker` | Drives new order ingestion into the in-memory books. |
| `order.cancelled.v1` | `orders-service` | `matching-engine` | `ticker` | Removes resting orders from matching book + DB. |
| `trade.executed.v1` | `matching-engine` | `orders-service`, `market-data-consumer`, `transaction-processor` | `ticker` | Drives fills, candle aggregation, and transaction journaling. |
| `transaction.recorded.v1` | `transaction-processor` | `portfolio-service` | `tradeId:userId:side` | One trade produces two records: one BUY-side, one SELL-side. |

DLT convention:
- `matching-engine`, `transaction-processor`, and `portfolio-service` all route poison records to `<source-topic>.DLT` on the same partition.
- `market-data-consumer` does not have checked-in DLT wiring in the files reviewed for this pass.

---

## Shared transport assumptions

- Broker: Redpanda using Kafka-compatible APIs.
- Payloads are JSON.
- Most consumers disable type headers and set an explicit default value type.
- At-least-once delivery is assumed.
- Idempotency is handled in application code and database ledgers, not via Kafka exactly-once features.

Observed serialization differences:
- `matching-engine` emits `trade.executed.v1.timestamp` as `OffsetDateTime`.
- `transaction-processor` consumes `trade.executed.v1.timestamp` as `Instant`.
- This appears to rely on Jackson time parsing compatibility rather than a single canonical timestamp type.

---

## 1) `order.placed.v1`

### Producer

- Service: `orders-service`
- Code path: `OrderService.place(...)` -> `OrderProducer.publish(...)`
- Partition key: `evt.ticker()`

### Consumer

- Service: `matching-engine`
- Consumer class: `OrderPlacedConsumer`
- Consumer group: `matching-engine` in Compose (`KAFKA_CONSUMER_GROUP=matching-engine`)
- Ack mode: manual

### Payload fields currently produced

Source class in `orders-service`: `com.tradestream.orders_service.events.OrderPlaced`

Fields consumed by `matching-engine` DTO:
- `orderId` : UUID
- `userId` : UUID
- `ticker` : String
- `side` : enum (`BUY` or `SELL`)
- `orderType` or `type` : enum (`LIMIT` or `MARKET`)
- `timeInForce` : enum
- `price` : decimal or `null` for MARKET
- `quantity` : decimal
- `timestamp` : Instant

Implementation detail:
- `matching-engine` explicitly accepts both `orderType` and `type` via `@JsonAlias`.
- That is a compatibility shim in the consumer DTO, not a formal schema guarantee.

### Idempotency behavior

- `matching-engine` first looks for a Kafka header named `eventId`.
- If no header exists, it falls back to a synthetic UUID derived from `topic|partition|offset`.
- It stores `(topic, messageId)` in `processed_messages`.

Important gap:
- `orders-service` does not attach an `eventId` header here.
- The fallback is therefore usually offset-derived, not business-stable.

### Ordering assumption

- Producer uses `ticker` as the Kafka key.
- The intent is to keep all events for a given ticker in the same partition so matching can treat that ticker as effectively serialized.

### Current failure handling

- `matching-engine` retries with exponential backoff and then sends the record to `order.placed.v1.DLT`.
- `IllegalArgumentException` is non-retryable and goes straight to DLT.

---

## 2) `order.cancelled.v1`

### Producer

- Service: `orders-service`
- Code path: `OrderService.cancelOrder(...)`
- Partition key: `saved.getTicker()`

Emission rule:
- A cancel event is only emitted when an order transitions from `NEW` to `CANCELED`.
- Re-cancel on an already canceled order returns the order without emitting another event.
- Only `NEW` orders can be canceled; other statuses raise an error.

### Consumer

- Service: `matching-engine`
- Consumer class: `OrderCancelledConsumer`
- Consumer group: `matching-engine`
- Ack mode: manual

### Payload fields currently produced/consumed

Source class in `orders-service`: `OrderCancelledEvent`

Fields:
- `orderId` : UUID
- `userId` : UUID
- `ticker` : String
- `quantity` : decimal
- `price` : decimal, nullable
- `timestamp` : Instant

### Idempotency behavior

- Same strategy as `order.placed.v1`:
  - prefer `eventId` header if present
  - otherwise synthesize from `topic|partition|offset`
  - record in `processed_messages`

Gap:
- The producer does not add an `eventId` header here either.

### Consumer-side semantic effect

- `matching-engine` updates the resting-order row status to `CANCELED`.
- It removes the order from the in-memory book for that ticker.
- If the order is unknown, the consumer logs a warning.

### Current failure handling

- Same DLT/backoff policy as `order.placed.v1`.

---

## 3) `trade.executed.v1`

### Producer

- Service: `matching-engine`
- Code path: `MatchingService.publishTrade(...)` -> `tradePublisher.publish(...)`
- Partition key: ticker

Emission rule:
- One match creates one trade event with a newly generated `tradeId`.
- The trade references one `buyOrderId` and one `sellOrderId`.

### Consumers

#### `orders-service`
- Consumer class: `TradeExecutedConsumer`
- Group: `orders-exec-consumer` by default
- Idempotency key: `(orderId, tradeId, ticker, timestamp)` inserted through `IngestedFillRepository.tryInsert(...)`
- Effect: update fill quantity, order status, last fill price

#### `market-data-consumer`
- Consumer class: `TradeExecutedListener`
- Group: `md-consumer`
- Idempotency key: `tradeId + ticker + timestamp` via `ingested_trades`
- Effect: upsert OHLCV candles for `1m`, `5m`, `1h`, `1d`; evict Redis latest cache keys

#### `transaction-processor`
- Consumer class: `TradeExecutedConsumer`
- Group: `txproc-journal` in Compose
- Idempotency key: `(topic, tradeId)` in `processed_messages`
- Effect: resolve users from `orders-service`, insert buyer and seller journal rows, emit two `transaction.recorded.v1` events

### Payload fields currently produced

Producer DTO in `matching-engine`: `TradeExecutedEvent`

Fields:
- `tradeId` : UUID
- `buyOrderId` : UUID
- `sellOrderId` : UUID
- `ticker` : String
- `price` : decimal
- `quantity` : decimal
- `timestamp` : `OffsetDateTime`

Consumer DTOs expect broadly the same shape, but not always the same Java time type.

### Contract caveats

- No checked-in `eventId` field or required `eventId` header.
- Downstream idempotency is therefore based on `tradeId`, not a distinct event ID.
- This is workable because `tradeId` is generated once per match, but it conflates trade identity and message identity.

### Current failure handling

- `transaction-processor` has DLT/backoff wiring.
- `orders-service` reviewed files do not expose explicit DLT wiring in this pass.
- `market-data-consumer` reviewed files do not expose explicit DLT wiring in this pass.

---

## 4) `transaction.recorded.v1`

### Producer

- Service: `transaction-processor`
- Code path: `TransactionService.processTrade(...)` -> `TransactionRecordedProducer.publish(...)`
- Partition key: `tradeId:userId:side`

Emission rule:
- Every processed trade produces two events:
  - one BUY-side transaction record
  - one SELL-side transaction record

### Consumer

- Service: `portfolio-service`
- Consumer class: `TransactionRecordedConsumer`
- Consumer group: `portfolio-svc`
- Ack mode: manual

### Payload fields currently produced

Producer DTO: `TransactionRecordedEvent`

Fields:
- `eventId` : UUID
- `tradeId` : UUID
- `orderId` : UUID
- `userId` : UUID
- `side` : String (`BUY` or `SELL`)
- `ticker` : String
- `quantity` : decimal
- `price` : decimal
- `executedAt` : Instant
- `version` : int (currently set to `1`)

### Idempotency behavior

- `portfolio-service` prefers `evt.getEventId()`.
- If absent in payload, it tries a Kafka `eventId` header.
- If still absent, it synthesizes a UUID from `topic|partition|offset`.
- It records `(topic, messageId)` in `processed_messages`.

Important note:
- This is the only reviewed event payload that already includes an explicit `eventId` field.

### Consumer-side semantic effect

- `BUY`:
  - increase quantity
  - recompute weighted average cost
- `SELL`:
  - clamp sell quantity to current long quantity
  - add realized PnL based on `price - avgCost`
  - reduce quantity
  - reset `avgCost` to `null` when flat

Non-goal implied by the current contract:
- This event stream projects long-only positions; it does not currently model short inventory.

### Current failure handling

- `portfolio-service` retries with exponential backoff and then sends poison records to `transaction.recorded.v1.DLT`.
- `IllegalArgumentException` is non-retryable.

---

## 5) Cross-topic design notes

### Partitioning strategy is domain-meaningful but not universal

- Orders and trades are keyed by `ticker`.
- Transaction records are keyed by `tradeId:userId:side`.

What that buys today:
- Matching and market-data consumers benefit from ticker locality.
- Portfolio projection benefits less from ticker ordering and more from per-user event uniqueness.

### Business identity and message identity are mixed in different ways

- `tradeId` acts as the dedupe key in several places.
- `eventId` exists only on `transaction.recorded.v1`.
- Other topics rely on missing-header fallbacks.

That is the main reason the current event contract should be treated as a code contract, not yet as a formal schema-governed platform contract.

---

## 6) Recommended next contract hardening steps

These are implementation recommendations, not statements about current behavior.

1. Add a mandatory `eventId` field to every event payload.
2. Stop relying on offset-derived fallback IDs except for telemetry/debug use.
3. Standardize time fields to one canonical type/format across all event DTOs.
4. Publish an explicit schema versioning policy per topic.
5. Add CI contract tests before introducing more downstream consumers.
