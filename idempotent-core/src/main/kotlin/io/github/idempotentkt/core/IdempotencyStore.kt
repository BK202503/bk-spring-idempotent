package io.github.idempotentkt.core

import java.time.Instant
import kotlin.time.Duration

/**
 * Storage contract for idempotency keys. Implementations must guarantee
 * atomic reservation: only one caller can transition a key from "unseen"
 * to "in flight" at a time. Built-in backends use either a coroutine
 * mutex (in-memory) or a `INSERT ... ON CONFLICT` style upsert (JDBC).
 */
interface IdempotencyStore {

    /**
     * Atomic reserve-or-lookup. The outcome determines what the caller does
     * next; see each [Reservation] subtype.
     */
    suspend fun reserve(key: String, bodyHash: String, ttl: Duration): Reservation

    /** Mark a reservation as complete and store the response for replay. */
    suspend fun complete(key: String, response: StoredResponse)

    /**
     * Look up an in-flight reservation. Used by `WAIT` callers polling
     * for the first request to finish.
     */
    suspend fun peek(key: String): Lookup
}

sealed class Reservation {
    /**
     * The caller is the first one to use this key. Run the handler, then
     * call [IdempotencyStore.complete].
     */
    data object New : Reservation()

    /** A previous request with the same key + body already completed. Replay [response]. */
    data class AlreadyComplete(val response: StoredResponse) : Reservation()

    /** Another request with the same key + body is currently running. */
    data class InFlight(val since: Instant) : Reservation()

    /**
     * A request with this key already exists, but its body hash differs.
     * This is almost always a client bug — reject with 422.
     */
    data class BodyMismatch(val storedBodyHash: String) : Reservation()
}

sealed class Lookup {
    data class Complete(val response: StoredResponse) : Lookup()
    data class Pending(val since: Instant) : Lookup()
    data object Missing : Lookup()
}
