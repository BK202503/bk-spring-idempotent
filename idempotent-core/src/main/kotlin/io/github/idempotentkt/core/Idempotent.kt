package io.github.idempotentkt.core

/**
 * Marks a Spring MVC or WebFlux endpoint as idempotent. The starter's
 * interceptor inspects the configured header on every request to the
 * annotated handler:
 *
 * - **First request with this key** — the handler runs, the response is
 *   stored, the response is returned to the caller.
 * - **Same key + same request body, already complete** — the handler is
 *   *not* invoked again; the stored response is replayed verbatim.
 * - **Same key + same request body, in flight** — depending on
 *   [onConcurrent], the caller either waits up to [maxWaitMillis] for the
 *   first request to finish, or is rejected immediately with `409 Conflict`.
 * - **Same key + different request body** — the caller is rejected with
 *   `422 Unprocessable Entity`. Reusing the key with a different payload
 *   is almost always a client bug.
 *
 * No key on the request → the annotation is a no-op (the handler runs
 * normally). This mirrors Stripe's semantics and means the annotation is
 * safe to add to existing endpoints without forcing every caller to start
 * sending the header right away.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Idempotent(
    val header: String = "Idempotency-Key",
    /** Time-to-live for the cached response in milliseconds. Default: 24 hours. */
    val ttlMillis: Long = 24L * 60L * 60L * 1000L,
    val onConcurrent: ConcurrentBehavior = ConcurrentBehavior.WAIT,
    /** When [onConcurrent] is [ConcurrentBehavior.WAIT], how long to wait. Default: 30 seconds. */
    val maxWaitMillis: Long = 30_000L,
)

enum class ConcurrentBehavior {
    /** Block the second caller up to `maxWaitMillis`, then replay or 409. */
    WAIT,

    /** Return 409 Conflict immediately for the concurrent request. */
    REJECT,
}
