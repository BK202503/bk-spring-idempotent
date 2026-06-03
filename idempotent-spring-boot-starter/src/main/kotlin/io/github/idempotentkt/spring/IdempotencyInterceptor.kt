package io.github.idempotentkt.spring

import io.github.idempotentkt.core.BodyHasher
import io.github.idempotentkt.core.Idempotent
import io.github.idempotentkt.core.IdempotentExecutor
import io.github.idempotentkt.core.StoredResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.coroutines.runBlocking
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import kotlin.time.Duration.Companion.milliseconds

/**
 * Pre-handle hook that consults the [IdempotentExecutor]. On a replay or
 * a 422/409 outcome the handler is not invoked at all; on a fresh run the
 * handler executes and the response is captured for storage in
 * `afterCompletion`.
 */
class IdempotencyInterceptor(
    private val executor: IdempotentExecutor,
) : HandlerInterceptor {

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val annotation = handler.findIdempotent() ?: return true
        val key = request.getHeader(annotation.header)
            ?: return true // no key supplied; pass through

        val cached = (request as? CachedBodyHttpServletRequest)?.cachedBody ?: ByteArray(0)
        val bodyHash = BodyHasher.sha256(cached)

        val outcome = runBlocking {
            executor.reserve(key, bodyHash, annotation.ttlMillis.milliseconds)
        }

        return when (outcome) {
            is IdempotentExecutor.Outcome.Fresh -> {
                request.setAttribute(ATTR_KEY, key)
                true
            }
            is IdempotentExecutor.Outcome.Replayed -> {
                writeStored(response, outcome.response)
                false
            }
            is IdempotentExecutor.Outcome.BodyMismatch -> {
                response.status = 422
                response.contentType = "text/plain;charset=UTF-8"
                response.writer.write(
                    "Idempotency-Key reuse with a different request body. Stored body hash: ${outcome.storedBodyHash}",
                )
                false
            }
            IdempotentExecutor.Outcome.Concurrent -> {
                response.status = HttpServletResponse.SC_CONFLICT
                response.contentType = "text/plain;charset=UTF-8"
                response.writer.write("Idempotency-Key is currently being processed by another request.")
                false
            }
        }
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?,
    ) {
        val key = request.getAttribute(ATTR_KEY) as? String ?: return
        if (ex != null) return
        val captured = response.findCapturer() ?: return
        val stored = StoredResponse(
            status = response.status,
            headers = captured.snapshotHeaders(),
            body = captured.capturedBody(),
        )
        runBlocking { executor.complete(key, stored) }
    }

    private fun Any.findIdempotent(): Idempotent? {
        if (this !is HandlerMethod) return null
        return getMethodAnnotation(Idempotent::class.java)
            ?: beanType.getAnnotation(Idempotent::class.java)
    }

    private fun HttpServletResponse.findCapturer(): CapturingHttpServletResponseWrapper? {
        var current: Any? = this
        while (current is jakarta.servlet.http.HttpServletResponseWrapper) {
            if (current is CapturingHttpServletResponseWrapper) return current
            current = current.response
        }
        return null
    }

    private fun writeStored(response: HttpServletResponse, stored: StoredResponse) {
        response.status = stored.status
        stored.headers.forEach { (name, values) ->
            values.forEach { response.addHeader(name, it) }
        }
        response.outputStream.write(stored.body)
        response.outputStream.flush()
    }

    companion object {
        internal const val ATTR_KEY = "idempotent.key"
    }
}
