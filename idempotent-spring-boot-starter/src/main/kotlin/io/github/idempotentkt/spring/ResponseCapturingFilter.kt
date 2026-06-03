package io.github.idempotentkt.spring

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Wraps the response so [IdempotencyInterceptor] can read the captured
 * bytes after the handler runs.
 */
class ResponseCapturingFilter : OncePerRequestFilter(), Ordered {

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + 101

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val wrapped = CapturingHttpServletResponseWrapper(response)
        try {
            filterChain.doFilter(request, wrapped)
        } finally {
            wrapped.flushBuffer()
        }
    }
}
