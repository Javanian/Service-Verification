package com.javanian.proof

import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/** Bound JSON bodies even for chunked requests; multipart has independent servlet limits. */
@Component
@Order(-105)
class JsonBodyLimit : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        if (!request.contentType.orEmpty().substringBefore(';').equals("application/json", true)) {
            chain.doFilter(request, response)
            return
        }
        val bytes = request.inputStream.readNBytes(65_537)
        if (bytes.size > 65_536) {
            response.status = 413
            response.contentType = "application/json"
            response.writer.write("{\"message\":\"JSON request must be at most 64 KiB.\"}")
            return
        }
        val wrapped =
            object : HttpServletRequestWrapper(request) {
                override fun getInputStream(): ServletInputStream {
                    val stream = ByteArrayInputStream(bytes)
                    return object : ServletInputStream() {
                        override fun read() = stream.read()

                        override fun isFinished() = stream.available() == 0

                        override fun isReady() = true

                        override fun setReadListener(listener: ReadListener) {
                            throw UnsupportedOperationException(
                                "Asynchronous request bodies are unsupported"
                            )
                        }
                    }
                }

                override fun getReader() =
                    BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            }
        chain.doFilter(wrapped, response)
    }
}
