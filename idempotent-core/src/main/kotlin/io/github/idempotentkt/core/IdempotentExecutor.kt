package io.github.idempotentkt.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Orchestrates the reserve → execute → complete flow for a single
 * idempotent operation. The executor itself is stateless; all state is
 * held in the [IdempotencyStore].
 */
class IdempotentExecutor(
    private val store: IdempotencyStore,
    private val metrics: IdempotencyMetrics = IdempotencyMetrics.NoOp,
) {
    private val log = LoggerFactory.getLogger(IdempotentExecutor::class.java)

    /**
     * Execute [block] under idempotency protection. The block receives the
     * caller-provided body hash via the store, never directly.
     *
     * @param key the client-supplied idempotency key
     * @param bodyHash a stable digest of the request payload
     * @param ttl how long the stored response is replayable
     * @param onConcurrent what to do when another caller is already running
     * @param maxWait when [onConcurrent] is [ConcurrentBehavior.WAIT], the upper bound on waiting
     * @param block the actual handler producing a [StoredResponse]
     */
    suspend fun execute(
        key: String,
        bodyHash: String,
        ttl: Duration,
        onConcurrent: ConcurrentBehavior,
        maxWait: Duration,
        block: suspend () -> StoredResponse,
    ): Outcome {
        return when (val reservation = store.reserve(key, bodyHash, ttl)) {
            Reservation.New -> runFresh(key, block)
            is Reservation.AlreadyComplete -> {
                metrics.replay(key)
                Outcome.Replayed(reservation.response)
            }
            is Reservation.InFlight -> handleConcurrent(key, onConcurrent, maxWait)
            is Reservation.BodyMismatch -> {
                metrics.bodyMismatch(key)
                Outcome.BodyMismatch(reservation.storedBodyHash)
            }
        }
    }

    private suspend fun runFresh(key: String, block: suspend () -> StoredResponse): Outcome {
        metrics.fresh(key)
        try {
            val response = block()
            store.complete(key, response)
            return Outcome.Fresh(response)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            log.warn("idempotent handler threw for key {} — reservation NOT marked complete", key, t)
            metrics.handlerFailure(key, t)
            throw t
        }
    }

    private suspend fun handleConcurrent(
        key: String,
        onConcurrent: ConcurrentBehavior,
        maxWait: Duration,
    ): Outcome {
        if (onConcurrent == ConcurrentBehavior.REJECT) {
            metrics.concurrent(key, waited = false)
            return Outcome.Concurrent
        }
        val deadline = System.nanoTime() + maxWait.inWholeNanoseconds
        var slept = Duration.ZERO
        while (System.nanoTime() < deadline) {
            when (val peek = store.peek(key)) {
                is Lookup.Complete -> {
                    metrics.concurrent(key, waited = true)
                    return Outcome.Replayed(peek.response)
                }
                is Lookup.Pending -> {
                    val step = nextBackoff(slept)
                    delay(step.inWholeMilliseconds)
                    slept += step
                }
                Lookup.Missing -> {
                    metrics.concurrent(key, waited = true)
                    return Outcome.Concurrent
                }
            }
        }
        metrics.concurrent(key, waited = true)
        return Outcome.Concurrent
    }

    private fun nextBackoff(elapsed: Duration): Duration =
        if (elapsed < 100.milliseconds) 10.milliseconds
        else if (elapsed < 1000.milliseconds) 50.milliseconds
        else 200.milliseconds

    /**
     * Interceptor-friendly variant: reserves a key and resolves any
     * existing entry without running a handler. Returns:
     *
     * - [Outcome.Fresh] with an empty payload meaning "you may proceed to
     *   run the handler; call [complete] after with the response"
     * - [Outcome.Replayed] / [Outcome.BodyMismatch] / [Outcome.Concurrent]
     *   meaning the handler should *not* run; the caller writes the
     *   appropriate response directly.
     */
    suspend fun reserve(
        key: String,
        bodyHash: String,
        ttl: Duration,
    ): Outcome = when (val reservation = store.reserve(key, bodyHash, ttl)) {
        Reservation.New -> {
            metrics.fresh(key)
            Outcome.Fresh(StoredResponse(0, emptyMap(), ByteArray(0)))
        }
        is Reservation.AlreadyComplete -> {
            metrics.replay(key)
            Outcome.Replayed(reservation.response)
        }
        is Reservation.InFlight -> {
            metrics.concurrent(key, waited = false)
            Outcome.Concurrent
        }
        is Reservation.BodyMismatch -> {
            metrics.bodyMismatch(key)
            Outcome.BodyMismatch(reservation.storedBodyHash)
        }
    }

    /** Persist the response for a key that was successfully reserved by [reserve]. */
    suspend fun complete(key: String, response: StoredResponse) {
        store.complete(key, response)
    }

    sealed class Outcome {
        /** Handler executed, response stored, return verbatim. */
        data class Fresh(val response: StoredResponse) : Outcome()

        /** A previous identical request finished; replay this response. */
        data class Replayed(val response: StoredResponse) : Outcome()

        /** Concurrent request not resolved in time. Caller should return 409. */
        data object Concurrent : Outcome()

        /** Key reused with a different body. Caller should return 422. */
        data class BodyMismatch(val storedBodyHash: String) : Outcome()
    }
}
