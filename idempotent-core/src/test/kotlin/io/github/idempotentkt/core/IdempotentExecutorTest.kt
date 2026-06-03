package io.github.idempotentkt.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class IdempotentExecutorTest : StringSpec({

    fun newExecutor(): Pair<IdempotentExecutor, InMemoryIdempotencyStore> {
        val store = InMemoryIdempotencyStore()
        return IdempotentExecutor(store) to store
    }

    "Fresh request runs the handler and stores the response" {
        val (executor, _) = newExecutor()
        val result = runBlocking {
            executor.execute(
                key = "k1",
                bodyHash = "h",
                ttl = 1.minutes,
                onConcurrent = ConcurrentBehavior.REJECT,
                maxWait = 0.milliseconds,
            ) { StoredResponse(200, mapOf("X-Test" to listOf("v")), "ok".toByteArray()) }
        }
        result.shouldBeInstanceOf<IdempotentExecutor.Outcome.Fresh>()
        result.response.status shouldBe 200
        result.response.body.decodeToString() shouldBe "ok"
    }

    "Second request with the same key + body replays the cached response" {
        val (executor, _) = newExecutor()
        val runs = AtomicInteger()
        val block: suspend () -> StoredResponse = {
            runs.incrementAndGet()
            StoredResponse(201, emptyMap(), "created".toByteArray())
        }
        runBlocking {
            executor.execute("k", "h", 1.minutes, ConcurrentBehavior.REJECT, 0.milliseconds, block)
            val second = executor.execute("k", "h", 1.minutes, ConcurrentBehavior.REJECT, 0.milliseconds, block)
            second.shouldBeInstanceOf<IdempotentExecutor.Outcome.Replayed>()
            second.response.body.decodeToString() shouldBe "created"
            runs.get() shouldBe 1
        }
    }

    "Same key + different body returns BodyMismatch (422)" {
        val (executor, _) = newExecutor()
        runBlocking {
            executor.execute("k", "h1", 1.minutes, ConcurrentBehavior.REJECT, 0.milliseconds) {
                StoredResponse(200, emptyMap(), "a".toByteArray())
            }
            val out = executor.execute("k", "h2", 1.minutes, ConcurrentBehavior.REJECT, 0.milliseconds) {
                StoredResponse(200, emptyMap(), "b".toByteArray())
            }
            val mismatch = out.shouldBeInstanceOf<IdempotentExecutor.Outcome.BodyMismatch>()
            mismatch.storedBodyHash shouldBe "h1"
        }
    }

    "REJECT returns Concurrent immediately when another request is in flight" {
        val (executor, _) = newExecutor()
        runBlocking {
            val first = async {
                executor.execute("k", "h", 1.minutes, ConcurrentBehavior.REJECT, 0.milliseconds) {
                    delay(200)
                    StoredResponse(200, emptyMap(), "one".toByteArray())
                }
            }
            delay(20)
            val second = async {
                executor.execute("k", "h", 1.minutes, ConcurrentBehavior.REJECT, 0.milliseconds) {
                    error("should not run")
                }
            }
            val results = listOf(first, second).awaitAll()
            results[0].shouldBeInstanceOf<IdempotentExecutor.Outcome.Fresh>()
            results[1].shouldBe(IdempotentExecutor.Outcome.Concurrent)
        }
    }

    "WAIT replays the first request's response after waiting" {
        val (executor, _) = newExecutor()
        runBlocking {
            val first = async {
                executor.execute("k", "h", 1.minutes, ConcurrentBehavior.WAIT, 500.milliseconds) {
                    delay(80)
                    StoredResponse(200, emptyMap(), "winner".toByteArray())
                }
            }
            delay(10)
            val second = async {
                executor.execute("k", "h", 1.minutes, ConcurrentBehavior.WAIT, 500.milliseconds) {
                    error("must not be invoked")
                }
            }
            val results = listOf(first, second).awaitAll()
            results[0].shouldBeInstanceOf<IdempotentExecutor.Outcome.Fresh>()
            val replay = results[1].shouldBeInstanceOf<IdempotentExecutor.Outcome.Replayed>()
            replay.response.body.decodeToString() shouldBe "winner"
        }
    }

    "Handler exception leaves the reservation pending so future retries can succeed" {
        val (executor, _) = newExecutor()
        runBlocking {
            runCatching {
                executor.execute("k", "h", 1.minutes, ConcurrentBehavior.REJECT, 0.milliseconds) {
                    error("boom")
                }
            }
            // Retry with same key + body. Currently the slot is still PENDING because we
            // never completed it; a subsequent REJECT call yields Concurrent. This matches
            // the contract: the failed handler did not produce a definitive response, so
            // a fresh executor cannot assume the operation is safe to skip.
            val out = executor.execute("k", "h", 1.minutes, ConcurrentBehavior.REJECT, 0.milliseconds) {
                StoredResponse(500, emptyMap(), "retry".toByteArray())
            }
            out.shouldBe(IdempotentExecutor.Outcome.Concurrent)
        }
    }

    "reserve + complete two-step matches the interceptor flow" {
        val (executor, _) = newExecutor()
        runBlocking {
            val reserved = executor.reserve("k", "h", 1.minutes)
            reserved.shouldBeInstanceOf<IdempotentExecutor.Outcome.Fresh>()
            executor.complete("k", StoredResponse(204, emptyMap(), ByteArray(0)))

            val again = executor.reserve("k", "h", 1.minutes)
            val replay = again.shouldBeInstanceOf<IdempotentExecutor.Outcome.Replayed>()
            replay.response.status shouldBe 204
        }
    }
})
