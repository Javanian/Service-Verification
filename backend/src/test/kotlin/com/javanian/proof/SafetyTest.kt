package com.javanian.proof

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class SafetyTest {
    @Test
    fun `login and public requests have separate bounded limits`() {
        val filter = LoginThrottle()
        fun request(path: String, method: String): MockHttpServletResponse {
            val response = MockHttpServletResponse()
            filter.doFilter(
                MockHttpServletRequest(method, path),
                response,
                jakarta.servlet.FilterChain { _, _ -> },
            )
            return response
        }
        repeat(20) { assertEquals(200, request("/api/login", "POST").status) }
        assertEquals(429, request("/api/login", "POST").status)
        repeat(120) { assertEquals(200, request("/api/session", "GET").status) }
        val blocked = request("/api/session", "GET")
        assertEquals(429, blocked.status)
        assertEquals("60", blocked.getHeader("Retry-After"))
        assertEquals("no-store", blocked.getHeader("Cache-Control"))
    }

    @Test
    fun `storage errors expose no SQL or connection secrets`() {
        val response =
            Errors()
                .storage(
                    DataAccessResourceFailureException("jdbc://private-host password=do-not-expose")
                )
        assertEquals(503, response.statusCode.value())
        assertFalse(response.body.toString().contains("do-not-expose"))
        assertTrue(response.body.toString().contains("Retry"))
    }

    @Test
    fun `oversized JSON is rejected before controller parsing`() {
        val request = MockHttpServletRequest("POST", "/api/jobs")
        request.contentType = "application/json"
        request.setContent(ByteArray(65_537))
        val response = MockHttpServletResponse()
        JsonBodyLimit()
            .doFilter(
                request,
                response,
                jakarta.servlet.FilterChain { _, _ ->
                    fail<Unit>("Oversized request reached controller")
                },
            )
        assertEquals(413, response.status)
    }
}
