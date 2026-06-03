package io.github.idempotentkt.storage.jdbc

import javax.sql.DataSource

enum class SqlDialect(internal val schemaResource: String) {
    H2("h2.sql"),
    POSTGRESQL("postgresql.sql"),
    ;

    companion object {
        fun detect(dataSource: DataSource): SqlDialect {
            dataSource.connection.use { conn ->
                val product = conn.metaData.databaseProductName?.lowercase().orEmpty()
                return when {
                    "postgresql" in product -> POSTGRESQL
                    "h2" in product -> H2
                    else -> error(
                        "Unsupported database product '$product'. Supported: H2, PostgreSQL. " +
                            "Pass an explicit SqlDialect to JdbcIdempotencyStore.",
                    )
                }
            }
        }
    }
}
