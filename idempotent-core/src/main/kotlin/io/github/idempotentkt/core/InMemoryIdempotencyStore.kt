package io.github.idempotentkt.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import kotlin.time.Duration

class InMemoryIdempotencyStore(private val clock: Clock = Clock.systemUTC()) : IdempotencyStore {

    private val mutex = Mutex()
    private val entries = LinkedHashMap<String, Entry>()

    override suspend fun reserve(key: String, bodyHash: String, ttl: Duration): Reservation = mutex.withLock {
        val now = Instant.now(clock)
        evictExpired(now)
        val existing = entries[key]
        if (existing == null) {
            entries[key] = Entry(
                bodyHash = bodyHash,
                state = State.Pending(now),
                expiresAt = now.plusMillis(ttl.inWholeMilliseconds),
            )
            return Reservation.New
        }
        if (existing.bodyHash != bodyHash) {
            return Reservation.BodyMismatch(existing.bodyHash)
        }
        when (val state = existing.state) {
            is State.Pending -> Reservation.InFlight(state.since)
            is State.Complete -> Reservation.AlreadyComplete(state.response)
        }
    }

    override suspend fun complete(key: String, response: StoredResponse): Unit = mutex.withLock {
        val existing = entries[key] ?: return@withLock
        entries[key] = existing.copy(state = State.Complete(response))
    }

    override suspend fun peek(key: String): Lookup = mutex.withLock {
        evictExpired(Instant.now(clock))
        val existing = entries[key] ?: return@withLock Lookup.Missing
        when (val state = existing.state) {
            is State.Pending -> Lookup.Pending(state.since)
            is State.Complete -> Lookup.Complete(state.response)
        }
    }

    suspend fun size(): Int = mutex.withLock { entries.size }

    suspend fun clear(): Unit = mutex.withLock { entries.clear() }

    private fun evictExpired(now: Instant) {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val (_, entry) = iterator.next()
            if (entry.expiresAt.isBefore(now)) iterator.remove()
        }
    }

    private data class Entry(val bodyHash: String, val state: State, val expiresAt: Instant)

    private sealed class State {
        data class Pending(val since: Instant) : State()
        data class Complete(val response: StoredResponse) : State()
    }
}
