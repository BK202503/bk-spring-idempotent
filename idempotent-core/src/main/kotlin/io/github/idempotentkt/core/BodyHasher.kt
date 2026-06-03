package io.github.idempotentkt.core

import java.security.MessageDigest

/**
 * Produces a stable SHA-256 hex digest of a request body. Used to detect
 * key-reuse-with-different-body, which is almost always a client bug and
 * surfaces as `422 Unprocessable Entity`.
 *
 * Empty/null bodies hash to the SHA-256 of the empty input. Two requests
 * with no body and the same key are considered "same body".
 */
object BodyHasher {
    private val HEX = "0123456789abcdef".toCharArray()
    private val EMPTY_BODY_HASH = digest(ByteArray(0))

    fun sha256(body: ByteArray?): String =
        if (body == null || body.isEmpty()) EMPTY_BODY_HASH else digest(body)

    private fun digest(input: ByteArray): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input)
        val hex = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            hex.append(HEX[v shr 4]).append(HEX[v and 0x0F])
        }
        return hex.toString()
    }
}
