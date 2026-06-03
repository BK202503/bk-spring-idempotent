package io.github.idempotentkt.storage.jdbc

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.StringSpec

class H2JdbcIdempotencyStoreTest : StringSpec({
    val ds = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:idem-h2-${System.nanoTime()};DB_CLOSE_DELAY=-1"
            driverClassName = "org.h2.Driver"
            maximumPoolSize = 4
        },
    )
    afterSpec { ds.close() }

    jdbcContractTests("h2", SqlDialect.H2) { ds }
})
