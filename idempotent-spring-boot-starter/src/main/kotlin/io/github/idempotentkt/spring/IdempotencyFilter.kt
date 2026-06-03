package io.github.idempotentkt.spring

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Buffers the request body so the [IdempotencyInterceptor] can hash it
 * and Spring's argument resolvers can still read it. Runs ahead of the
 * dispatcher (HIGHEST_PRECEDENCE + 100) so the wrapped request reaches
 * everything else.
 */
class IdempotencyFilter(private val maxBodyBytes: Int) : OncePerRequestFilter(), Ordered {

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + 100

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val ct = request.contentType
        val method = request.method
        val mayHaveBody = method == "POST" || method == "PUT" || method == "PATCH" || method == "DELETE"
        if (!mayHaveBody) {
            filterChain.doFilter(request, response)
            return
        }
        val cached = request.inputStream.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                total += n
                if (total > maxBodyBytes) {
                    response.status = HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE
                    response.writer.write("Request body exceeds idempotent.max-body-bytes ($maxBodyBytes)")
                    return
                }
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
        val wrapped = CachedBodyHttpServletRequest(request, cached)
        // Avoid the unused-variable warning when ct is null
        if (ct == null) { /* no-op */ }
        filterChain.doFilter(wrapped, response)
    }
}
