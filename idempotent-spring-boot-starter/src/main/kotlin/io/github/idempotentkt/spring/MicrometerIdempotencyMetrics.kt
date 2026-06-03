package io.github.idempotentkt.spring

import io.github.idempotentkt.core.IdempotencyMetrics
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags

class MicrometerIdempotencyMetrics(private val registry: MeterRegistry) : IdempotencyMetrics {

    override fun fresh(key: String) {
        registry.counter("idempotent.fresh").increment()
    }

    override fun replay(key: String) {
        registry.counter("idempotent.replay").increment()
    }

    override fun concurrent(key: String, waited: Boolean) {
        registry.counter(
            "idempotent.concurrent",
            Tags.of("waited", waited.toString()),
        ).increment()
    }

    override fun bodyMismatch(key: String) {
        registry.counter("idempotent.body_mismatch").increment()
    }

    override fun handlerFailure(key: String, error: Throwable) {
        registry.counter(
            "idempotent.handler_failed",
            Tags.of("exception", error::class.simpleName ?: "Throwable"),
        ).increment()
    }
}
