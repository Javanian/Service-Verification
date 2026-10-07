package com.javanian.proof

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/** Single-instance protection. The deployment edge must also enforce rate limits. */
@Component
@Order(-110)
class LoginThrottle : OncePerRequestFilter() {
    private data class Window(var start: Long, var count: Int)

    private val attempts = mutableMapOf<String, Window>()

    @Synchronized
    private fun allowed(address: String): Boolean {
        val now = System.currentTimeMillis()
        attempts.entries.removeIf { now - it.value.start > 60_000 }
        if (attempts.size >= 10_000 && address !in attempts) return false
        val window = attempts.getOrPut(address) { Window(now, 0) }
        window.count++
        return window.count <= 20
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        if (request.requestURI.startsWith("/api/")) response.setHeader("Cache-Control", "no-store")
        if (
            request.requestURI == "/api/login" &&
                request.method == "POST" &&
                !allowed(request.remoteAddr)
        ) {
            response.status = 429
            response.setHeader("Retry-After", "60")
            response.contentType = "application/json"
            response.writer.write(
                "{\"message\":\"Too many sign-in attempts. Wait one minute and retry.\"}"
            )
            return
        }
        chain.doFilter(request, response)
    }
}
