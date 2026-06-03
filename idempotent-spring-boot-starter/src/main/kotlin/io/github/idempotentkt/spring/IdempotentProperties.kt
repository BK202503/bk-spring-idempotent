package io.github.idempotentkt.spring

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "idempotent")
data class IdempotentProperties(
    /** Storage backend selection. */
    val storage: Storage = Storage.JDBC,
    /** When true, the starter creates the idempotency_keys table at startup. */
    val initializeSchema: Boolean = true,
    /** Override the idempotency_keys table name. */
    val tableName: String = "idempotency_keys",
    /** Publish Micrometer metrics. */
    val metricsEnabled: Boolean = true,
    /** Maximum request body size (bytes) the interceptor will buffer for hashing + storage. */
    val maxBodyBytes: Int = 1024 * 1024,
) {
    enum class Storage { JDBC, IN_MEMORY }
}
