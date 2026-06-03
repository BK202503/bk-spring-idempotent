package io.github.idempotentkt.storage.jdbc

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.idempotentkt.core.IdempotencyStore
import io.github.idempotentkt.core.Lookup
import io.github.idempotentkt.core.Reservation
import io.github.idempotentkt.core.StoredResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import javax.sql.DataSource
import kotlin.time.Duration

/**
 * JDBC-backed [IdempotencyStore]. Each row is uniquely keyed by `key_id`,
 * so racing `insert` calls naturally produce one winner (the loser gets a
 * PK violation, which the store interprets as "already reserved by
 * someone else, look up the existing row").
 */
class JdbcIdempotencyStore(
    private val dataSource: DataSource,
    private val mapper: ObjectMapper = jacksonObjectMapper(),
    private val tableName: String = "idempotency_keys",
    private val clock: Clock = Clock.systemUTC(),
    dialect: SqlDialect? = null,
) : IdempotencyStore {

    val dialect: SqlDialect = dialect ?: SqlDialect.detect(dataSource)
    private val log = LoggerFactory.getLogger(JdbcIdempotencyStore::class.java)

    fun initializeSchema() {
        val resource = "/io/github/idempotentkt/storage/jdbc/schema/${dialect.schemaResource}"
        val sql = JdbcIdempotencyStore::class.java.getResource(resource)
            ?.readText()
            ?.let { if (tableName == "idempotency_keys") it else it.replace("idempotency_keys", tableName) }
            ?: error("$resource not found")
        dataSource.connection.use { conn ->
            conn.createStatement().use { st ->
                for (stmt in sql.split(";").map { it.trim() }.filter { it.isNotEmpty() }) {
                    st.execute(stmt)
                }
            }
        }
    }

    override suspend fun reserve(key: String, bodyHash: String, ttl: Duration): Reservation = withContext(Dispatchers.IO) {
        val now = Instant.now(clock)
        val expiresAt = now.plusMillis(ttl.inWholeMilliseconds)

        dataSource.connection.use { conn ->
            evictExpired(conn, now)
            val insert = "INSERT INTO $tableName (key_id, body_hash, status, reserved_at, expires_at) VALUES (?, ?, 'PENDING', ?, ?)"
            try {
                conn.prepareStatement(insert).use { ps ->
                    ps.setString(1, key)
                    ps.setString(2, bodyHash)
                    ps.setTimestamp(3, Timestamp.from(now))
                    ps.setTimestamp(4, Timestamp.from(expiresAt))
                    ps.executeUpdate()
                }
                Reservation.New
            } catch (e: SQLException) {
                // Duplicate key — someone else reserved it. Read what's there.
                if (!isUniqueViolation(e)) throw e
                lookupExistingForReserve(conn, key, bodyHash)
                    ?: error("Insert failed for $key but row not present — should not happen")
            }
        }
    }

    private fun lookupExistingForReserve(
        conn: java.sql.Connection,
        key: String,
        bodyHash: String,
    ): Reservation? {
        return conn.prepareStatement("SELECT * FROM $tableName WHERE key_id = ?").use { ps ->
            ps.setString(1, key)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return@use null
                val storedHash = rs.getString("body_hash")
                if (storedHash != bodyHash) return@use Reservation.BodyMismatch(storedHash)
                val status = rs.getString("status")
                when (status) {
                    "PENDING" -> Reservation.InFlight(rs.getTimestamp("reserved_at").toInstant())
                    "COMPLETE" -> Reservation.AlreadyComplete(readResponse(rs))
                    else -> error("Unknown idempotency status: $status")
                }
            }
        }
    }

    override suspend fun complete(key: String, response: StoredResponse): Unit = withContext(Dispatchers.IO) {
        val now = Instant.now(clock)
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                UPDATE $tableName
                   SET status = 'COMPLETE',
                       response_status = ?,
                       response_headers = ?,
                       response_body = ?,
                       completed_at = ?
                 WHERE key_id = ?
                """.trimIndent(),
            ).use { ps ->
                ps.setInt(1, response.status)
                ps.setString(2, mapper.writeValueAsString(response.headers))
                ps.setBytes(3, response.body)
                ps.setTimestamp(4, Timestamp.from(now))
                ps.setString(5, key)
                val rows = ps.executeUpdate()
                if (rows == 0) log.warn("complete() found no reservation for key {} — was it evicted?", key)
            }
        }
    }

    override suspend fun peek(key: String): Lookup = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            evictExpired(conn, Instant.now(clock))
            conn.prepareStatement("SELECT * FROM $tableName WHERE key_id = ?").use { ps ->
                ps.setString(1, key)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) return@use Lookup.Missing
                    when (rs.getString("status")) {
                        "PENDING" -> Lookup.Pending(rs.getTimestamp("reserved_at").toInstant())
                        "COMPLETE" -> Lookup.Complete(readResponse(rs))
                        else -> Lookup.Missing
                    }
                }
            }
        }
    }

    private fun readResponse(rs: ResultSet): StoredResponse {
        val status = rs.getInt("response_status")
        val headersJson = rs.getString("response_headers") ?: "{}"
        val headers: Map<String, List<String>> = mapper.readValue(headersJson)
        val body = rs.getBytes("response_body") ?: ByteArray(0)
        return StoredResponse(status, headers, body)
    }

    private fun evictExpired(conn: java.sql.Connection, now: Instant) {
        conn.prepareStatement("DELETE FROM $tableName WHERE expires_at < ?").use { ps ->
            ps.setTimestamp(1, Timestamp.from(now))
            ps.executeUpdate()
        }
    }

    private fun isUniqueViolation(e: SQLException): Boolean {
        // PostgreSQL: 23505. H2: 23505 (post v2). Some old H2: 23001.
        val sqlState = e.sqlState ?: return false
        return sqlState == "23505" || sqlState == "23001" || sqlState.startsWith("23")
    }
}
