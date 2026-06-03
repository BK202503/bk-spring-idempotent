package io.github.idempotentkt.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class BodyHasherTest : StringSpec({
    "same input produces same hash" {
        BodyHasher.sha256("hello".toByteArray()) shouldBe BodyHasher.sha256("hello".toByteArray())
    }
    "different input produces different hash" {
        BodyHasher.sha256("hello".toByteArray()) shouldNotBe BodyHasher.sha256("world".toByteArray())
    }
    "empty and null body share a stable hash" {
        BodyHasher.sha256(null) shouldBe BodyHasher.sha256(ByteArray(0))
    }
    "hash is 64 hex characters" {
        BodyHasher.sha256("anything".toByteArray()).length shouldBe 64
        BodyHasher.sha256("anything".toByteArray()).all { it in '0'..'9' || it in 'a'..'f' } shouldBe true
    }
})
