package io.github.idempotentkt.example

import io.github.idempotentkt.core.Idempotent
import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@SpringBootApplication
class PaymentsApiApp

fun main(args: Array<String>) {
    SpringApplication.run(PaymentsApiApp::class.java, *args)
}

data class ChargeRequest(val userId: String, val amountCents: Long, val description: String)

data class ChargeResponse(val chargeId: String, val userId: String, val amountCents: Long, val attempt: Int)

@RestController
class PaymentsController {

    private val log = LoggerFactory.getLogger(javaClass)
    private val attempts = AtomicInteger()

    @PostMapping("/charges")
    @Idempotent
    fun charge(@RequestBody req: ChargeRequest): ChargeResponse {
        val n = attempts.incrementAndGet()
        log.info("charging user={} amount={} attempt={}", req.userId, req.amountCents, n)
        return ChargeResponse(
            chargeId = "ch_${UUID.randomUUID()}",
            userId = req.userId,
            amountCents = req.amountCents,
            attempt = n,
        )
    }
}
