package io.github.idempotentkt.spring

import io.github.idempotentkt.core.Idempotent
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest(
    classes = [IdempotentEndToEndTest.TestApp::class],
    properties = [
        "spring.datasource.url=jdbc:h2:mem:idem-e2e;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
    ],
)
@AutoConfigureMockMvc
class IdempotentEndToEndTest {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var counter: AtomicInteger

    @Test
    fun `first request executes handler and returns response`() {
        counter.set(0)
        mvc.perform(
            post("/charge")
                .header("Idempotency-Key", "k-1")
                .contentType("application/json")
                .content("""{"amount":100}"""),
        )
            .andExpect(status().isOk)
            .andExpect(content().json("""{"charged":100,"runs":1}"""))
        counter.get() shouldBe 1
    }

    @Test
    fun `replay with same key + same body does not re-run handler`() {
        counter.set(0)
        val req = post("/charge")
            .header("Idempotency-Key", "k-2")
            .contentType("application/json")
            .content("""{"amount":50}""")

        mvc.perform(req).andExpect(status().isOk)
        mvc.perform(req).andExpect(status().isOk).andExpect(content().json("""{"charged":50,"runs":1}"""))

        counter.get() shouldBe 1
    }

    @Test
    fun `same key + different body returns 422`() {
        counter.set(0)
        mvc.perform(
            post("/charge")
                .header("Idempotency-Key", "k-3")
                .contentType("application/json")
                .content("""{"amount":1}"""),
        ).andExpect(status().isOk)

        mvc.perform(
            post("/charge")
                .header("Idempotency-Key", "k-3")
                .contentType("application/json")
                .content("""{"amount":2}"""),
        ).andExpect(status().isUnprocessableEntity)

        counter.get() shouldBe 1
    }

    @Test
    fun `no key header bypasses idempotency entirely`() {
        counter.set(0)
        mvc.perform(
            post("/charge")
                .contentType("application/json")
                .content("""{"amount":10}"""),
        ).andExpect(status().isOk)
        mvc.perform(
            post("/charge")
                .contentType("application/json")
                .content("""{"amount":10}"""),
        ).andExpect(status().isOk)
        counter.get() shouldBe 2
    }

    data class ChargeReq(val amount: Long)
    data class ChargeRes(val charged: Long, val runs: Int)

    @RestController
    class ChargeController(private val counter: AtomicInteger) {
        @PostMapping("/charge")
        @Idempotent
        fun charge(@RequestBody req: ChargeReq): ChargeRes =
            ChargeRes(charged = req.amount, runs = counter.incrementAndGet())
    }

    @Configuration
    @EnableAutoConfiguration
    open class TestApp {
        @Bean open fun counter(): AtomicInteger = AtomicInteger()
        @Bean open fun controller(counter: AtomicInteger): ChargeController = ChargeController(counter)
    }
}
