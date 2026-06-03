package io.github.idempotentkt.spring

import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.charset.Charset

/**
 * Wraps a request so its body can be read twice: once by the interceptor
 * to hash it, and again by Spring's argument resolvers when the handler
 * eventually deserializes it.
 */
class CachedBodyHttpServletRequest(
    request: HttpServletRequest,
    val cachedBody: ByteArray,
) : HttpServletRequestWrapper(request) {

    override fun getInputStream(): ServletInputStream {
        val backing = ByteArrayInputStream(cachedBody)
        return object : ServletInputStream() {
            override fun isFinished() = backing.available() == 0
            override fun isReady() = true
            override fun setReadListener(listener: ReadListener) = Unit
            override fun read(): Int = backing.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = backing.read(b, off, len)
        }
    }

    override fun getReader(): BufferedReader {
        val charset = characterEncoding?.let(Charset::forName) ?: Charsets.UTF_8
        return BufferedReader(InputStreamReader(inputStream, charset))
    }
}
