package io.github.idempotentkt.storage.jdbc

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.StringSpec
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Runs the shared contract against a real PostgreSQL via testcontainers.
 * Skips when Docker is unreachable, fails loud when `IDEMPOTENT_REQUIRE_DOCKER=1`.
 */
class PostgresJdbcIdempotencyStoreTest : StringSpec({
    val requireDocker = System.getenv("IDEMPOTENT_REQUIRE_DOCKER") == "1"

    val container = runCatching {
        PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("idem")
            .withUsername("idem")
            .withPassword("idem")
            .also { it.start() }
    }

    if (container.isFailure) {
        val err = container.exceptionOrNull()
        if (requireDocker) {
            throw IllegalStateException(
                "IDEMPOTENT_REQUIRE_DOCKER=1 but Docker is not reachable: ${err?.message}",
                err,
            )
        }
        "[postgres] suite skipped: Docker not reachable (${err?.javaClass?.simpleName})"
            .config(enabled = false) { }
    } else {
        val pg = container.getOrThrow()
        val ds = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = pg.jdbcUrl
                username = pg.username
                password = pg.password
                driverClassName = pg.driverClassName
                maximumPoolSize = 4
            },
        )

        afterSpec {
            ds.close()
            pg.stop()
        }

        jdbcContractTests("postgres", SqlDialect.POSTGRESQL) { ds }
    }
})
