# Changelog

All notable changes to this project will be documented here. Format based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] — 2026-06-04

Initial public release.

### Added
- `idempotent-core`: `@Idempotent` annotation, `IdempotencyStore` SPI with
  sealed `Reservation` / `Lookup` outcomes, `IdempotentExecutor`,
  `InMemoryIdempotencyStore`, SHA-256 `BodyHasher`, `IdempotencyMetrics` SPI.
- `idempotent-storage-jdbc`: JDBC store with H2 / PostgreSQL auto-detected
  dialects, dialect-specific schemas, lazy TTL eviction, atomic reservation
  via INSERT race.
- `idempotent-spring-boot-starter`: body-buffering filter, response-capturing
  filter, MVC `HandlerInterceptor`, autoconfigure with `IdempotentProperties`,
  Micrometer metrics, default JDBC schema initialization.
- `examples/payments-api`: runnable Spring Boot REST demo of the annotation.
- CI workflow that runs the full suite including PostgreSQL via testcontainers.

[Unreleased]: https://github.com/BK202503/bk-spring-idempotent/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/BK202503/bk-spring-idempotent/releases/tag/v0.1.0
