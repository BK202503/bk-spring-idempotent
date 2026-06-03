# Contributing

Thanks for considering a contribution. Please open an issue before starting
on anything substantial so we can align on direction.

## Quick start

```bash
git clone https://github.com/BK202503/bk-spring-idempotent.git
cd bk-spring-idempotent
./gradlew build
```

JDK 17 required. The Gradle wrapper handles Gradle itself.

## Tests

```bash
./gradlew test                                  # unit + Spring autoconfigure
IDEMPOTENT_REQUIRE_DOCKER=1 ./gradlew test      # also Postgres via testcontainers
```

The Docker-backed suite skips gracefully without `IDEMPOTENT_REQUIRE_DOCKER=1`
or a reachable Docker daemon, so the default run stays fast on developer
machines.

On macOS with Docker Desktop, if testcontainers can't find the socket,
write a one-line `~/.testcontainers.properties`:

```properties
docker.host=unix:///Users/<you>/.docker/run/docker.sock
```

## Layout

| Module                            | Scope                                                          |
|-----------------------------------|----------------------------------------------------------------|
| `idempotent-core`                 | Annotation, executor, SPIs, in-memory backend. Pure Kotlin.    |
| `idempotent-storage-jdbc`         | JDBC backend (H2 / Postgres) and Jackson codec.                |
| `idempotent-spring-boot-starter`  | Autoconfigure + filters + interceptor.                         |
| `examples/payments-api`           | Runnable demo. Not published.                                  |

`idempotent-core` must stay Kotlin-only with no Spring or Jackson dependency.
Anything that requires those belongs in the JDBC module or a new module.

## Adding a storage backend

1. New module `idempotent-storage-<name>` depending on `idempotent-core`.
2. Implement `IdempotencyStore` with atomic reserve semantics.
3. Reuse the contract suite by porting the `jdbcContractTests` helper.

## Style

- Kotlin official style, 4-space indent.
- Public API uses `suspend`, not `CompletableFuture` / blocking.
- New public types get one paragraph of KDoc explaining intent and invariants.
- Tests use Kotest `StringSpec`.

## Pull requests

- One concern per PR.
- CI must pass; testcontainers suites included.
- Update `CHANGELOG.md` under `## [Unreleased]`.

## License

By contributing you agree your contributions will be licensed under Apache 2.0.
