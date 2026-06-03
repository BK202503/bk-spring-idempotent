package io.github.idempotentkt.storage.jdbc

import io.github.idempotentkt.core.Lookup
import io.github.idempotentkt.core.Reservation
import io.github.idempotentkt.core.StoredResponse
import io.kotest.core.spec.style.scopes.StringSpecRootScope
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import javax.sql.DataSource
import kotlin.time.Duration.Companion.minutes

internal fun StringSpecRootScope.jdbcContractTests(
    label: String,
    dialect: SqlDialect,
    dataSource: () -> DataSource,
) {
    fun freshStore(): JdbcIdempotencyStore {
        val ds = dataSource()
        ds.connection.use { c ->
            c.createStatement().use { it.execute("DROP TABLE IF EXISTS idempotency_keys") }
        }
        val store = JdbcIdempotencyStore(ds, dialect = dialect)
        store.initializeSchema()
        return store
    }

    "[$label] dialect auto-detect matches explicit dialect" {
        SqlDialect.detect(dataSource()) shouldBe dialect
    }

    "[$label] reserve returns New on first call and InFlight on the second with the same key" {
        val store = freshStore()
        runBlocking {
            store.reserve("k", "h", 1.minutes) shouldBe Reservation.New
            val second = store.reserve("k", "h", 1.minutes)
            second.shouldBeInstanceOf<Reservation.InFlight>()
        }
    }

    "[$label] complete then reserve again returns AlreadyComplete with the stored response" {
        val store = freshStore()
        runBlocking {
            store.reserve("k", "h", 1.minutes)
            store.complete("k", StoredResponse(201, mapOf("X" to listOf("y")), "ok".toByteArray()))
            val again = store.reserve("k", "h", 1.minutes)
            val complete = again.shouldBeInstanceOf<Reservation.AlreadyComplete>()
            complete.response.status shouldBe 201
            complete.response.headers["X"] shouldBe listOf("y")
            complete.response.body.decodeToString() shouldBe "ok"
        }
    }

    "[$label] reserve with mismatched body hash returns BodyMismatch" {
        val store = freshStore()
        runBlocking {
            store.reserve("k", "h1", 1.minutes)
            val second = store.reserve("k", "h2", 1.minutes)
            val mismatch = second.shouldBeInstanceOf<Reservation.BodyMismatch>()
            mismatch.storedBodyHash shouldBe "h1"
        }
    }

    "[$label] peek reports Pending then Complete" {
        val store = freshStore()
        runBlocking {
            store.reserve("k", "h", 1.minutes)
            store.peek("k").shouldBeInstanceOf<Lookup.Pending>()
            store.complete("k", StoredResponse(200, emptyMap(), "x".toByteArray()))
            store.peek("k").shouldBeInstanceOf<Lookup.Complete>()
        }
    }

    "[$label] preserves a large binary response body exactly" {
        val store = freshStore()
        val bytes = ByteArray(64 * 1024) { (it % 251).toByte() }
        runBlocking {
            store.reserve("k", "h", 1.minutes)
            store.complete("k", StoredResponse(200, emptyMap(), bytes))
            val again = store.reserve("k", "h", 1.minutes)
            val complete = again.shouldBeInstanceOf<Reservation.AlreadyComplete>()
            complete.response.body.contentEquals(bytes) shouldBe true
        }
    }
}
