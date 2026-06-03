# spring-idempotent-kt

[![CI](https://github.com/BK202503/bk-spring-idempotent/actions/workflows/ci.yml/badge.svg)](https://github.com/BK202503/bk-spring-idempotent/actions/workflows/ci.yml)
[![JitPack](https://jitpack.io/v/BK202503/bk-spring-idempotent.svg)](https://jitpack.io/#BK202503/bk-spring-idempotent)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)

Stripe-style **`@Idempotent`** for Spring Boot. One annotation, production-ready
idempotency-key handling for any Spring MVC endpoint.

```kotlin
@RestController
class PaymentsController(private val payments: PaymentService) {

    @PostMapping("/charges")
    @Idempotent                                       // ← that's the whole API
    fun charge(@RequestBody req: ChargeRequest): ChargeResponse =
        payments.charge(req)
}
```

What the annotation gives you, for free:

```
First request  →  Idempotency-Key: abc-123   → handler runs, response cached, returned
Same key, same body, retried   →             → cached response replayed verbatim
Same key, *different* body                   → 422 Unprocessable Entity
Same key, still in flight                    → 409 Conflict  (or wait, configurable)
Different key                                → handler runs as a new request
No key header                                → handler runs as a normal endpoint
```

The semantics mirror [Stripe's idempotency contract](https://stripe.com/docs/api/idempotent_requests),
which has become the de-facto standard for HTTP APIs that mutate state.

## Why this exists

Every non-trivial HTTP API needs idempotency keys. Without them, network
retries silently double-charge, duplicate-create, or send the same email
twice. With them, callers can safely retry any request.

The Kotlin / Spring Boot options today are:

- **Roll your own** — most teams do. The first version is 50 lines, the
  *correct* version is 500 lines (concurrent races, body hash mismatch,
  response replay, TTL eviction, distributed storage).
- **Stripe-mason / similar Java libs** — exist, but ergonomically Java-first
  and rarely WebFlux-friendly. None ship a `suspend`-native API or a
  Spring Boot 3 autoconfigure starter.
- **API gateways** — some (Kong, Tyk) ship idempotency plugins. Heavy
  ops surface; doesn't help in-process tests; doesn't know the response
  body to replay.

`spring-idempotent-kt` is the small in-process middle ground:

- One annotation. No XML, no manual interceptor wiring.
- `IdempotencyStore` SPI. Ships with JDBC (H2 / Postgres auto-detected)
  and in-memory backends. Bring your own for Redis / Mongo.
- Sealed `Outcome` types. Compiler-forced handling of Fresh / Replayed /
  Concurrent / BodyMismatch.
- Spring Boot autoconfigure with Micrometer metrics.
- Coroutine-native core. Suspend functions all the way down; the MVC
  interceptor bridges via `runBlocking` so it works with traditional
  blocking controllers too.

## Install

### JitPack (recommended pre-1.0)

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.BK202503.bk-spring-idempotent:idempotent-spring-boot-starter:v0.1.0")
}
```

Or `main-SNAPSHOT` to track the latest commit on `main`.

### Maven Central (planned)

`io.github.idempotentkt:idempotent-spring-boot-starter:0.1.0` will be the
coordinate once the Sonatype namespace is verified.

## Quick start

### 1. Add the starter

```kotlin
implementation("com.github.BK202503.bk-spring-idempotent:idempotent-spring-boot-starter:v0.1.0")
```

### 2. Annotate the endpoint

```kotlin
@RestController
class OrdersController(private val orders: OrderService) {

    @PostMapping("/orders")
    @Idempotent
    fun createOrder(@RequestBody req: CreateOrderRequest): OrderResponse =
        orders.create(req)
}
```

That's it. The autoconfigure registers the body-buffering filter, the
response-capturing filter, the `HandlerInterceptor`, and the
`IdempotencyStore` (JDBC by default, in-memory for tests).

### 3. Call it

```bash
# First call — handler runs
curl -X POST localhost:8080/orders \
  -H 'Idempotency-Key: order-2026-06-04-abc' \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-A","qty":1}'

# Retry with the same key + body — handler does NOT run, cached response replays
curl -X POST localhost:8080/orders \
  -H 'Idempotency-Key: order-2026-06-04-abc' \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-A","qty":1}'

# Same key, different body — 422 Unprocessable Entity (client bug)
curl -X POST localhost:8080/orders \
  -H 'Idempotency-Key: order-2026-06-04-abc' \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-B","qty":1}'
```

## Configuration

```yaml
idempotent:
  storage: JDBC                # JDBC (default) or IN_MEMORY
  initialize-schema: true      # create idempotency_keys at startup
  table-name: idempotency_keys # override if you want a custom name
  metrics-enabled: true        # publish Micrometer counters
  max-body-bytes: 1048576      # 1 MiB; requests larger than this 413 immediately
```

Per-endpoint overrides via the annotation:

```kotlin
@Idempotent(
    header = "X-My-Idempotency-Key",      // default "Idempotency-Key"
    ttlMillis = 6L * 60L * 60L * 1000L,    // default 24h
    onConcurrent = ConcurrentBehavior.WAIT, // or REJECT for 409
    maxWaitMillis = 5_000L,
)
```

## Storage

| Backend       | Module                              | Tested against        |
|---------------|-------------------------------------|-----------------------|
| In-memory     | `idempotent-core`                   | unit                  |
| JDBC — H2     | `idempotent-storage-jdbc`           | unit                  |
| JDBC — Postgres | `idempotent-storage-jdbc`         | testcontainers (CI)   |
| Redis         | `idempotent-storage-redis`          | planned               |
| MongoDB       | `idempotent-storage-mongo`          | planned               |

The JDBC backend auto-detects the SQL dialect from `DataSource` metadata
and picks the matching schema. The schema is intentionally tiny:

```sql
CREATE TABLE idempotency_keys (
    key_id          VARCHAR(255) PRIMARY KEY,
    body_hash       VARCHAR(64)  NOT NULL,
    status          VARCHAR(16)  NOT NULL,   -- PENDING / COMPLETE
    response_status INTEGER      NULL,
    response_headers TEXT        NULL,        -- JSON
    response_body   BYTEA        NULL,
    reserved_at     TIMESTAMP    NOT NULL,
    completed_at    TIMESTAMP    NULL,
    expires_at      TIMESTAMP    NOT NULL
);
```

Expired rows are evicted lazily on each `reserve` / `peek`, so no
background sweeper is required. If you'd rather sweep on a schedule,
the table is safe to `DELETE FROM idempotency_keys WHERE expires_at < now()`.

To plug in your own backend, implement `IdempotencyStore`:

```kotlin
interface IdempotencyStore {
    suspend fun reserve(key: String, bodyHash: String, ttl: Duration): Reservation
    suspend fun complete(key: String, response: StoredResponse)
    suspend fun peek(key: String): Lookup
}
```

The `reserve` call must be **atomic** — only one caller may transition
a key from "unseen" to "in flight". JDBC uses an `INSERT ... ON CONFLICT`
race; Redis would use `SET ... NX`.

## Observability

When Micrometer is on the classpath:

- `idempotent.fresh{}` — handler executed
- `idempotent.replay{}` — cached response served
- `idempotent.concurrent{waited}` — concurrent request (and whether it waited or rejected immediately)
- `idempotent.body_mismatch{}` — same key, different body (422)
- `idempotent.handler_failed{exception}` — handler threw; reservation stays PENDING

Disable with `idempotent.metrics-enabled=false`.

## Comparison

| Feature                    | spring-idempotent-kt | Stripe-style libs (Java) | API gateway plugin |
|----------------------------|----------------------|--------------------------|--------------------|
| Single annotation           | ✓                    | partial                  | n/a                |
| Coroutine-native core       | ✓                    | ✗                        | n/a                |
| Spring Boot autoconfig      | ✓                    | partial                  | n/a                |
| Response body replay        | ✓                    | ✓                        | ✗                  |
| Body hash mismatch (422)    | ✓                    | varies                   | ✗                  |
| Concurrent wait/reject      | ✓                    | ✗                        | ✗                  |
| Per-request TTL             | ✓                    | varies                   | global only        |
| In-process (no gateway)     | ✓                    | ✓                        | ✗                  |
| Storage SPI                 | ✓ (JDBC / Redis / …) | mostly Redis-only        | gateway-specific   |

## Testing

```bash
./gradlew test                                   # unit + autoconfigure
IDEMPOTENT_REQUIRE_DOCKER=1 ./gradlew test       # also Postgres via testcontainers
```

The Postgres suite skips gracefully when Docker isn't reachable.

## Running the example

```bash
./gradlew :examples:payments-api:bootRun

# in another terminal:
curl -v -X POST localhost:8080/charges \
  -H 'Idempotency-Key: k-1' \
  -H 'Content-Type: application/json' \
  -d '{"userId":"u-1","amountCents":1999,"description":"coffee"}'

# repeat the same curl — the server returns the same chargeId and attempt=1
```

## Status

Pre-1.0. The annotation, `IdempotencyStore` SPI, and `Outcome` sealed
class should be considered stable; small breaking changes are still
possible until 1.0.

## License

Apache License 2.0 — see [LICENSE](./LICENSE).
