package io.github.idempotentkt.spring

import jakarta.servlet.ServletOutputStream
import jakarta.servlet.WriteListener
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper
import java.io.ByteArrayOutputStream
import java.io.PrintWriter

/**
 * Buffers response bytes and headers so the interceptor can persist them
 * after the handler runs. Wraps a real response so the bytes still flow
 * back to the client.
 */
class CapturingHttpServletResponseWrapper(response: HttpServletResponse) : HttpServletResponseWrapper(response) {

    private val captured = ByteArrayOutputStream()
    private val tee = TeeServletOutputStream(super.getOutputStream(), captured)
    private var writer: PrintWriter? = null

    override fun getOutputStream(): ServletOutputStream = tee

    override fun getWriter(): PrintWriter {
        if (writer == null) {
            val charset = characterEncoding ?: Charsets.UTF_8.name()
            writer = PrintWriter(java.io.OutputStreamWriter(tee, charset), true)
        }
        return writer!!
    }

    override fun flushBuffer() {
        writer?.flush()
        tee.flush()
        super.flushBuffer()
    }

    fun capturedBody(): ByteArray {
        writer?.flush()
        tee.flush()
        return captured.toByteArray()
    }

    fun snapshotHeaders(): Map<String, List<String>> =
        headerNames.associateWith { name -> getHeaders(name).toList() }

    private class TeeServletOutputStream(
        private val real: ServletOutputStream,
        private val capture: ByteArrayOutputStream,
    ) : ServletOutputStream() {
        override fun isReady(): Boolean = real.isReady
        override fun setWriteListener(listener: WriteListener) = real.setWriteListener(listener)
        override fun write(b: Int) {
            real.write(b)
            capture.write(b)
        }
        override fun write(b: ByteArray, off: Int, len: Int) {
            real.write(b, off, len)
            capture.write(b, off, len)
        }
        override fun flush() {
            real.flush()
        }
    }
}
