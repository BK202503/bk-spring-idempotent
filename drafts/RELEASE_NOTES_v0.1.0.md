# v0.1.0 — initial public release

`spring-idempotent-kt` — Stripe-style `@Idempotent` for Spring Boot. One
annotation, production-ready idempotency-key handling for any Spring MVC
endpoint.

## What's in the box

- **`idempotent-core`** — `@Idempotent` annotation, `IdempotencyStore` SPI
  with sealed `Reservation` / `Lookup` outcomes, `IdempotentExecutor`,
  `InMemoryIdempotencyStore`, SHA-256 `BodyHasher`, `IdempotencyMetrics`.
- **`idempotent-storage-jdbc`** — JDBC backend with H2 / PostgreSQL
  dialect auto-detection from the `DataSource`, lazy TTL eviction, atomic
  reservation via an INSERT race.
- **`idempotent-spring-boot-starter`** — body-buffering filter,
  response-capturing filter, MVC `HandlerInterceptor`, autoconfigure,
  `IdempotentProperties`, Micrometer metrics.
- **`examples/payments-api`** — runnable REST demo.

## Semantics (Stripe-compatible)

| Request                             | Result                                       |
|-------------------------------------|----------------------------------------------|
| First call with `Idempotency-Key`   | handler runs, response cached, returned      |
| Same key + same body                | cached response replayed verbatim            |
| Same key + different body           | `422 Unprocessable Entity`                   |
| Same key, still in flight           | `409 Conflict` (or wait, configurable)       |
| No key header                       | handler runs as a normal endpoint            |

## Install (JitPack)

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}
dependencies {
    implementation("com.github.BK202503.bk-spring-idempotent:idempotent-spring-boot-starter:v0.1.0")
}
```

## Verified

- H2 (unit), PostgreSQL 16 (testcontainers, CI), Spring Boot 3.3.5 / Kotlin 2.0.
- CI green on every push; testcontainers under `IDEMPOTENT_REQUIRE_DOCKER=1`.

## Known limitations

- WebFlux filter not yet shipped (MVC only). Planned for 0.2.
- Redis backend not yet shipped. Planned for 0.2.
- Maven Central still pending; install via JitPack today.

Issues and PRs very welcome: https://github.com/BK202503/bk-spring-idempotent
