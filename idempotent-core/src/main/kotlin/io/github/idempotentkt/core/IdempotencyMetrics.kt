package io.github.idempotentkt.core

interface IdempotencyMetrics {
    fun fresh(key: String) {}
    fun replay(key: String) {}
    fun concurrent(key: String, waited: Boolean) {}
    fun bodyMismatch(key: String) {}
    fun handlerFailure(key: String, error: Throwable) {}

    companion object {
        val NoOp: IdempotencyMetrics = object : IdempotencyMetrics {}
    }
}
