package io.github.idempotentkt.core

/**
 * Snapshot of a completed HTTP response, kept so the same idempotency key
 * can replay it byte-for-byte on a retry. The store treats this as an
 * opaque payload.
 */
data class StoredResponse(
    val status: Int,
    val headers: Map<String, List<String>>,
    val body: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StoredResponse) return false
        return status == other.status &&
            headers == other.headers &&
            body.contentEquals(other.body)
    }

    override fun hashCode(): Int {
        var h = status
        h = 31 * h + headers.hashCode()
        h = 31 * h + body.contentHashCode()
        return h
    }
}
