# Devlog

## 2025-08-12 05:08:09 +0100 — commit `d227e77923d2bc0a633371fdd4c6338a557c7737`

This chunk is a single corrective commit layered directly on top of the first `matching-engine` and `orders-service` implementations. The changes are not a new service bootstrap. They are a follow-up stabilization pass aimed at making the trade pipeline survive real multi-leg executions, repeated Kafka delivery, DLT inspection, and restart/recovery scenarios.

The work is split across four visible areas:

- matching-engine idempotency and consumer fixes
- orders-service fill application redesign
- market-data-consumer numeric-type alignment
- new shell-level end-to-end test scripts

The scope is grounded in:

- [`services/matching-engine/`](services/matching-engine)
- [`services/orders-service/`](services/orders-service)
- [`services/market-data-consumer/src/main/java/com/tradestream/market_data_consumer/service/AggregationService.java`](services/market-data-consumer/src/main/java/com/tradestream/market_data_consumer/service/AggregationService.java)
- [`docker-compose.yml`](docker-compose.yml)
- three new test scripts at repo root

## The compose wiring changed to permit the matching engine’s bean graph to start

The only compose change in this commit is in [`docker-compose.yml`](docker-compose.yml):

- `SPRING_MAIN_ALLOW_CIRCULAR_REFERENCES: "true"` was added to the `matching-engine` service environment

That is a small diff but an important signal. It means the developer encountered a Spring bean dependency cycle in the current engine configuration and chose to unblock startup by allowing circular references rather than first refactoring the involved beans.

The evidence here is strong about the symptom but weak about the exact cycle. The patch does not show a stack trace or the failing bean graph. The safest evidence-based statement is:

- before `d227e77923d2bc0a633371fdd4c6338a557c7737`, the matching engine was configured without circular-reference allowance
- after this commit, compose explicitly opts into it, implying startup pressure from the current wiring

## The matching engine’s message dedup model was widened from global `message_id` uniqueness to per-topic uniqueness

The biggest internal matching-engine change is the redesign of `processed_messages`.

Before this commit, the evidence from the previous engine bootstrap showed:

- `processed_messages.message_id` was the primary key
- the entity [`ProcessedMessage.java`](services/matching-engine/src/main/java/com/tradestream/matching_engine/domain/ProcessedMessage.java) used `messageId` as `@Id`
- repository checks were simple `existsById(messageId)`

That model assumes a given message UUID should never appear on more than one topic.

After `d227e77923d2bc0a633371fdd4c6338a557c7737`, the model changed in both schema and JPA mapping.

### Schema migration `V2__processed_messages_topic_msgid.sql` introduced a surrogate primary key and `(topic, message_id)` uniqueness

[`services/matching-engine/src/main/resources/db/migration/V2__processed_messages_topic_msgid.sql`](services/matching-engine/src/main/resources/db/migration/V2__processed_messages_topic_msgid.sql) does all of the following:

1. adds `topic varchar(200)` if missing
2. adds `id bigserial` if missing
3. drops the existing primary key on `processed_messages`
4. makes `id` the new primary key
5. enforces `topic NOT NULL`
6. adds unique constraint `uk_processed_topic_msgid` on `(topic, message_id)`
7. creates an index on `(topic, message_id)`

The generated-resource mirror at [`services/matching-engine/bin/main/db/migration/V2__processed_messages_topic_msgid.sql`](services/matching-engine/bin/main/db/migration/V2__processed_messages_topic_msgid.sql) shows this migration also passed through the local build.

This is a direct before -> after change in idempotency semantics:

- before: dedup key was just `message_id`
- after: dedup key became `(topic, message_id)`

That matters because the engine consumes at least two topics:

- `order.placed.v1`
- `order.cancelled.v1`

Under the old model, the same UUID appearing on both topics would collide in the dedup table even if those were semantically different events. After this migration, those events can coexist safely.

### The `ProcessedMessage` entity was rewritten to match the new schema

[`services/matching-engine/src/main/java/com/tradestream/matching_engine/domain/ProcessedMessage.java`](services/matching-engine/src/main/java/com/tradestream/matching_engine/domain/ProcessedMessage.java) changed from:

- `@Id UUID messageId`

to:

- `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id`
- `String topic`
- `UUID messageId`
- `OffsetDateTime receivedAt`

and adds a table-level unique constraint:

- `uk_processed_topic_msgid(topic, message_id)`

So the entity was not merely extended. Its primary identity model changed entirely.

### Repository checks moved from primary-key existence to topic-scoped existence

[`ProcessedMessageRepository.java`](services/matching-engine/src/main/java/com/tradestream/matching_engine/persistence/ProcessedMessageRepository.java) changed from:

- `JpaRepository<ProcessedMessage, UUID>`

to:

- `JpaRepository<ProcessedMessage, Long>`
- `boolean existsByTopicAndMessageId(String topic, UUID messageId)`

That directly tracks the schema change above.

## Matching-engine consumers were updated to use topic-aware deduplication and stronger fallback IDs

Both engine consumers changed materially:

- [`OrderPlacedConsumer.java`](services/matching-engine/src/main/java/com/tradestream/matching_engine/stream/OrderPlacedConsumer.java)
- [`OrderCancelledConsumer.java`](services/matching-engine/src/main/java/com/tradestream/matching_engine/stream/OrderCancelledConsumer.java)

### Before this commit, both consumers used weak fallback and global `existsById`

In the pre-change code visible in the diff, both consumers:

- extracted `eventId` from Kafka headers if present
- otherwise fell back to `orderId` if present
- otherwise generated a random UUID
- checked `msgRepo.existsById(messageId)`
- saved `ProcessedMessage(messageId, receivedAt)`

That earlier fallback behavior meant malformed messages without usable IDs could still be processed, but the dedup key would be unstable across re-delivery if it had to fall all the way back to `UUID.randomUUID()`.

### After this commit, fallback moved to deterministic record identity

In both consumers, the fallback logic now becomes:

1. try `eventId` header
2. if absent or invalid, return `null`
3. if still null, synthesize a deterministic UUID from:
   - `topic`
   - `partition`
   - `offset`

using:

- `UUID.nameUUIDFromBytes((topic + "|" + partition + "|" + offset).getBytes(StandardCharsets.UTF_8))`

This is a meaningful behavioral correction. Before, a malformed record with no usable ID could dedup inconsistently across retries because `UUID.randomUUID()` changes on each attempt. After the patch, the fallback ID is stable for that exact Kafka record location.

### Dedup is now explicitly topic-aware in both consumers

Each consumer now checks:

- `msgRepo.existsByTopicAndMessageId(rec.topic(), messageId)`

and saves:

- `ProcessedMessage.topic = rec.topic()`
- `ProcessedMessage.messageId = messageId`

That closes the schema loop described above. The new entity model, migration, and repository method are all exercised by the listeners in the same commit.

### Cancel consumer logging became much more explicit

[`OrderCancelledConsumer.java`](services/matching-engine/src/main/java/com/tradestream/matching_engine/stream/OrderCancelledConsumer.java) also gained detailed logging around:

- consumed topic/key/partition/offset/value
- duplicate suppression
- applied cancel details (`orderId`, `ticker`, `quantity`, `price`)
- missing `orderId`
- invalid `eventId` header parsing

This matters because cancellation timing was clearly being debugged in this period, and the logging change exposes that directly.

The event object itself also widened in the same commit.

## The cancel-event payload was expanded from bare `orderId` to a fuller order snapshot

[`services/matching-engine/src/main/java/com/tradestream/matching_engine/dto/OrderCancelledEvent.java`](services/matching-engine/src/main/java/com/tradestream/matching_engine/dto/OrderCancelledEvent.java) previously contained only:

- `UUID orderId`

After this commit it also carries:

- `UUID userId`
- `String ticker`
- `BigDecimal quantity`
- `BigDecimal price`
- `Instant timestamp`

The current matching-engine `cancel(...)` path still only needs `orderId` to act, but the consumer now logs the extra fields, and the testing scripts in this chunk explicitly wait for and inspect cancel events on Kafka.

So before -> after:

- before: cancel events were minimal identifiers
- after: cancel events became richer diagnostic/contract objects even though the engine still used only the ID for state mutation

## `OrderPlacedEvent` was relaxed to accept both `type` and `orderType`

[`services/matching-engine/src/main/java/com/tradestream/matching_engine/dto/OrderPlacedEvent.java`](services/matching-engine/src/main/java/com/tradestream/matching_engine/dto/OrderPlacedEvent.java) changed the `orderType` field to:

- `@JsonProperty("orderType")`
- `@JsonAlias({"type","orderType"})`

This is a compatibility patch.

The newly added root-level test scripts in this same commit post order JSON using:

- `"type":"LIMIT"`
- `"type":"MARKET"`

but the original DTO field was named `orderType`.

So this patch removed a contract mismatch that would otherwise cause deserialization failure or silent null fields depending on caller behavior.

This is one of the clearest cause-and-effect links in the chunk:

- new tests and existing upstream payloads use `type`
- matching engine originally expected `orderType`
- commit `d227e77923d2bc0a633371fdd4c6338a557c7737` adds aliasing so both forms work

## `MatchingService` itself was not redesigned, but it was instrumented heavily for runtime diagnosis

[`services/matching-engine/src/main/java/com/tradestream/matching_engine/matching/MatchingService.java`](services/matching-engine/src/main/java/com/tradestream/matching_engine/matching/MatchingService.java) did not gain new matching rules in this chunk. The visible behavior remains the same:

- warm-start loading
- cancel by `orderId`
- FOK pre-check
- matching loop
- IOC/MARKET remainder cancellation
- resting of residual LIMIT quantity

What changed is the operational visibility.

The commit adds logging for:

- number of active orders loaded at startup
- each order loaded into an in-memory book
- incoming order handling
- FOK rejection
- each match found
- partial requeue of resting orders
- fully filled outcome
- IOC remainder cancellation
- MARKET remainder cancellation
- resting of residual quantity
- cancel request reception
- cancel application or missing-order warning

This is evidence of a debugging phase, not a new algorithm phase.

The code also changes one small but concrete behavior-reporting detail in cancellation:

- before, `removed = true` was not tracked
- after, the method logs `removedFromBook=true`

The evidence does not prove whether removal could ever fail in a meaningful way; the method still sets a hardcoded `boolean removed = true`. So this logging line is better understood as instrumentation than as a richer correctness check.

## DLT consumption was made more robust against missing headers and key-type mismatch

Two matching-engine files work together here:

- [`BytesListenerFactoryConfig.java`](services/matching-engine/src/main/java/com/tradestream/matching_engine/config/BytesListenerFactoryConfig.java)
- [`DltLoggingConsumer.java`](services/matching-engine/src/main/java/com/tradestream/matching_engine/stream/DltLoggingConsumer.java)

### The bytes listener factory was rebuilt from `KafkaProperties` rather than copying another consumer factory

Before this commit, `BytesListenerFactoryConfig` accepted:

- `ConsumerFactory<?, ?> base`

and cloned its configuration properties, then replaced both key and value deserializers with byte-array deserializers.

After this commit, it instead:

- builds a fresh map from `KafkaProperties.buildConsumerProperties()`
- sets key deserializer to `StringDeserializer`
- sets value deserializer to `ByteArrayDeserializer`
- defaults `auto.offset.reset` to `earliest`

This is a meaningful change in wiring strategy:

- before: DLT byte-consumer behavior piggybacked on whatever the main consumer factory already looked like
- after: the DLT byte-consumer gets its own independently built configuration

The comment added in the patch is explicit that this is meant to avoid more bean wiring and new cycles. That pairs neatly with the new `SPRING_MAIN_ALLOW_CIRCULAR_REFERENCES` compose setting: the engine was under pressure from bean-graph complexity in this period.

### `DltLoggingConsumer` now tolerates missing DLT metadata headers

[`DltLoggingConsumer.java`](services/matching-engine/src/main/java/com/tradestream/matching_engine/stream/DltLoggingConsumer.java) changed in three ways:

1. record key type changed from `byte[]` to `String`
2. DLT headers are now marked `required = false`
3. log formatting uses `String.valueOf(...)` for nullable header values

This means the DLT logger became more defensive:

- before: it assumed exception/original-topic/original-offset headers existed
- after: it can still log a record even when those headers are absent

That is a reliability fix rather than a feature addition.

### Stream logging level for matching-engine consumers was explicitly enabled

[`services/matching-engine/src/main/resources/application.yml`](services/matching-engine/src/main/resources/application.yml) and the generated copy under `bin/main` both gained:

- `logging.level.com.tradestream.matching_engine.stream: INFO`

This complements the extra consumer/matching logs described above. The code now emits more useful events, and the config ensures that at least the stream package logs will actually surface.

## Orders-service changed its execution-event model from “one trade has one order” to “one trade has two legs”

The largest business-level correction in this commit is in `orders-service`.

### The inbound trade DTO was rewritten to match the matching-engine’s output shape

[`services/orders-service/src/main/java/com/tradestream/orders_service/dto/TradeExecuted.java`](services/orders-service/src/main/java/com/tradestream/orders_service/dto/TradeExecuted.java) changed from a shape containing:

- `tradeId`
- `orderId`
- `userId`
- `ticker`
- `price`
- `quantity`
- `side`
- `timestamp`

to a shape containing:

- `tradeId`
- `buyOrderId`
- `sellOrderId`
- `ticker`
- `price`
- `quantity`
- `timestamp`

This is a fundamental contract correction.

Before this commit, `orders-service` was consuming execution events as if each trade referred to a single order row. But the matching-engine event model created on 2025-08-11 clearly emits two order references:

- buy side
- sell side

After `d227e77923d2bc0a633371fdd4c6338a557c7737`, the orders-service DTO finally matches that multi-leg trade contract.

## Orders-service replaced trade-level deduplication with per-order-per-trade deduplication

This is the most important internal orders-service change in the chunk.

### Before the commit, dedup was keyed only by `trade_id`

The deleted files make the old model explicit:

- [`IngestedTrade.java`](services/orders-service/src/main/java/com/tradestream/orders_service/domain/IngestedTrade.java) stored one row per `trade_id`
- [`IngestedTradeRepository.java`](services/orders-service/src/main/java/com/tradestream/orders_service/repo/IngestedTradeRepository.java) inserted into `ingested_trades` with primary key `trade_id`

That means the old orders-service consumer could only safely apply a trade once in total, not once per order leg.

Given the new matching-engine event shape with both `buyOrderId` and `sellOrderId`, that old dedup design was insufficient: one `tradeId` may legitimately need to update two orders.

### After the commit, dedup became `(order_id, trade_id)`

The new migration [`V4__ingested_fills.sql`](services/orders-service/src/main/resources/db/migration/V4__ingested_fills.sql) creates:

- `ingested_fills`

with primary key:

- `(order_id, trade_id)`

and the follow-up migration [`V5__drop_ingested_trades.sql`](services/orders-service/src/main/resources/db/migration/V5__drop_ingested_trades.sql) removes the old `ingested_trades` table.

The generated-resource copies under `bin/main` show the same migration pair was built locally.

This is a direct before -> after correction:

- before: a trade was deduped once globally
- after: a trade is deduped separately for each order it touches

### The JPA model changed to composite-key `IngestedFill`

The old `IngestedTrade` entity was deleted and replaced with:

- [`IngestedFill.java`](services/orders-service/src/main/java/com/tradestream/orders_service/domain/IngestedFill.java)
- [`IngestedFillId.java`](services/orders-service/src/main/java/com/tradestream/orders_service/domain/IngestedFillId.java)

`IngestedFillId` embeds:

- `orderId`
- `tradeId`

and `IngestedFill` stores:

- embedded ID
- `ticker`
- `ts`

That model matches the new schema exactly.

### Repository insert logic changed accordingly

[`IngestedFillRepository.java`](services/orders-service/src/main/java/com/tradestream/orders_service/repo/IngestedFillRepository.java) inserts:

- `(order_id, trade_id, ticker, ts)`

with:

- `ON CONFLICT (order_id, trade_id) DO NOTHING`

The old `IngestedTradeRepository` was deleted entirely.

## The orders-service trade consumer was rewritten to apply each trade to both legs

[`services/orders-service/src/main/java/com/tradestream/orders_service/kafka/TradeExecutedConsumer.java`](services/orders-service/src/main/java/com/tradestream/orders_service/kafka/TradeExecutedConsumer.java) changed substantially.

### Before this commit, the consumer assumed a single `orderId`

The old code path:

1. inserted `(tradeId, orderId, ticker, timestamp)` into `ingested_trades`
2. locked exactly one order row by `t.orderId()`
3. applied fill quantity and price to that single order
4. ignored duplicate `tradeId`

That model cannot correctly represent a matched trade that fills both a buyer and a seller order.

### After this commit, the consumer processes buy and sell legs separately

The new `onExecuted(TradeExecuted t)` does:

1. initialize `touched = false`
2. if `buyOrderId` exists:
   - call `applyOnce(tradeId, buyOrderId, ticker, timestamp, quantity, price)`
3. if `sellOrderId` exists:
   - call `applyOnce(tradeId, sellOrderId, ticker, timestamp, quantity, price)`
4. if neither leg changed anything new:
   - log duplicate-ignore message

The extracted `applyOnce(...)` method:

1. inserts `(orderId, tradeId, ticker, ts)` into `ingested_fills`
2. if insert count is zero, returns `false`
3. locks the target order row
4. if order is terminal (`CANCELED` or `FILLED`), returns `true`
5. if remaining quantity is already zero, returns `true`
6. caps applied execution quantity at remaining quantity
7. calls `order.applyFill(exec)`
8. updates `lastFillPrice` and `updatedAt`
9. saves the order
10. logs applied fill details

If the order row does not exist, it:

- logs a warning
- still treats the event as handled because the dedup row has already been inserted

This is the core correctness repair in the chunk. Before, a single trade could only advance one order row. After, the same trade can legitimately and idempotently advance both buyer and seller orders exactly once each.

## The market-data consumer was adjusted to stop re-boxing already-decimal execution values

[`services/market-data-consumer/src/main/java/com/tradestream/market_data_consumer/service/AggregationService.java`](services/market-data-consumer/src/main/java/com/tradestream/market_data_consumer/service/AggregationService.java) changed:

- `BigDecimal.valueOf(t.price())` -> `t.price()`
- `BigDecimal.valueOf(t.quantity())` -> `t.quantity()`

This is a narrow but important compatibility correction.

The earlier version of `TradeExecuted` in at least one service used primitive-like numeric types, which would justify `BigDecimal.valueOf(...)`. By this point in the pipeline, `price` and `quantity` are already `BigDecimal` in the DTOs touched by this chunk.

So before -> after:

- before: aggregation still assumed primitive/boxed numeric fields
- after: it consumes `BigDecimal` directly, aligning with the newer execution-event contract

## This commit also introduced the first broad shell-level end-to-end scenario suite

Three new executable scripts were added at repo root:

- [`e2e_scenarios.sh`](e2e_scenarios.sh)
- [`e2e_trade_pipeline_test.sh`](e2e_trade_pipeline_test.sh)
- [`manual_cancel_test.sh`](manual_cancel_test.sh)

These are not tiny helpers. They document what the developer believed the system should now do after the correctness changes above.

### `e2e_scenarios.sh` codified nine concrete behaviors

[`e2e_scenarios.sh`](e2e_scenarios.sh) brings up:

- `orders-service`
- `matching-engine`
- `market-data-consumer`
- supporting Postgres instances
- `redis`
- `redpanda`

and then runs nine scenarios:

1. partial fill
2. IOC partial
3. IOC with no liquidity
4. FOK insufficient liquidity
5. MARKET order execution
6. cancel flow
7. idempotency replay
8. recovery after matching-engine restart
9. DLT poison message handling

This script is especially valuable as evidence because it shows what the system-level bugs likely were around this time:

- cancel propagation timing
- duplicate trade application
- restart warm-load correctness
- DLT routing and observability

The script also explicitly comments on policy for cancellation:

- “you do NOT allow cancelling partially-filled orders (only NEW)”

That policy is stated in the script, not in matching-engine code. Since the chunk evidence does not show a matching-engine cancel-state guard being added here, the script should be read as an expectation of overall system behavior rather than proof that matching-engine alone enforces it.

### `e2e_trade_pipeline_test.sh` added a narrower pipeline smoke test

[`e2e_trade_pipeline_test.sh`](e2e_trade_pipeline_test.sh) focuses on:

- posting a resting sell order
- posting a crossing buy order
- watching `trade.executed.v1`
- querying market-data for the latest candle

This script uses payloads that include:

- `orderId`
- `side`
- `type`
- `timeInForce`

That is another concrete reason the `OrderPlacedEvent` JSON alias fix mattered in the same commit.

### `manual_cancel_test.sh` isolates cancel timing against real Kafka lag

[`manual_cancel_test.sh`](manual_cancel_test.sh) is a focused operational script for:

1. placing a resting sell
2. arming a one-shot consumer on `order.cancelled.v1`
3. issuing cancel through the orders API
4. printing the actual cancel Kafka record
5. verifying matching-engine lag on `order.cancelled.v1` is zero
6. sending a crossing buy
7. checking whether a trade still occurs

This is highly specific debugging workflow evidence. It shows the developer was not only interested in nominal cancellation but in the race between:

- cancel publication
- matching-engine catch-up
- later crossing orders

That timing sensitivity also explains the extra logs added to matching-engine consumers and the move to deterministic record-derived fallback IDs.

## The matching-engine’s stream package logging was enabled in the same commit because the new scripts depended on visible runtime evidence

This point is worth separating because the commit is unusually coherent across code and shell tools.

The scripts added here repeatedly rely on being able to observe:

- consumed Kafka events
- DLT records
- matching-engine catch-up
- cancel application order
- duplicate suppression

In the same commit:

- consumers gained explicit `INFO` logs
- stream logging was enabled in `application.yml`
- DLT logging became more robust

So this was not random verbosity. It was a deliberate support layer for the new scenario-driven testing style introduced by the shell scripts.

## The net effect of the chunk was to reconcile the pipeline around multi-leg trades and more realistic Kafka behavior

Before commit `d227e77923d2bc0a633371fdd4c6338a557c7737`, the evidence in the patch shows several mismatches across the pipeline:

- matching-engine dedup assumed globally unique `message_id`
- matching-engine order placement DTO was stricter than some JSON producers using `type`
- DLT logging and bytes-factory wiring were more brittle
- orders-service execution consumption assumed one order per trade
- orders-service dedup prevented applying the same trade to two legitimate order legs
- market-data aggregation still expected primitive-like execution numerics

After the commit:

- matching-engine dedup became `(topic, message_id)` with deterministic fallback IDs
- matching-engine consumers recorded topic-aware processed-message rows
- order placement accepted both `type` and `orderType`
- DLT handling and stream logging became more operationally robust
- orders-service processed `buyOrderId` and `sellOrderId` separately
- orders-service dedup became `(order_id, trade_id)`
- market-data consumer consumed `BigDecimal` execution values directly
- root-level scripts encoded concrete pipeline scenarios to validate these behaviors

This was therefore not a generic “cleanup” commit despite the `Deploy` message. It was a targeted correctness and observability pass over the event-driven trade path, with the strongest evidence centered on idempotency model repair and two-leg trade application.
