@file:Suppress("unused")

package kim.jade.kotlinx.logger.integration.okhttp

import kim.jade.kotlinx.logger.LogLevel
import kim.jade.kotlinx.logger.Logger
import kim.jade.kotlinx.logger.context.LogContext
import kim.jade.kotlinx.logger.context.snapCurrentLogContext
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.http.promisesBody
import okio.Buffer
import okio.GzipSource
import java.io.EOFException
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlin.time.Clock

class OkHttpLogger(
    val clientName: String,
    val option: HttpLogOption = HttpLogOption(),
    private val logger: Logger = Logger.named("HttpClientLogger-$clientName"),
) : Interceptor {

    companion object {

        const val DEFAULT_MAX_BODY_BYTES: Long = 32L * 1024
    }

    data class HttpLogOption(
        var successLogLevel: LogLevel = LogLevel.DEBUG,
        var failLogLevel: LogLevel = LogLevel.WARNING,
        var includeRequestHeaders: Boolean = false,
        var includeRequestBody: Boolean = false,
        var includeResponseHeaders: Boolean = false,
        var includeResponseBody: Boolean = false,
        var combineLog: Boolean = true,
        var maxBodyBytes: Long = DEFAULT_MAX_BODY_BYTES,
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val connection = chain.connection()
        val request = chain.request()

        val logContext = snapCurrentLogContext() + request.tag(LogContext::class.java)

        val requestHeaders = if (option.includeRequestHeaders) request.headers.toMap() else emptyMap()
        val requestBody = capturedRequestBody(request)

        val requestTimestamp = Clock.System.now()
        val requestLog = HttpRequestLog(
            connection?.protocol()?.name ?: "",
            request.method,
            request.url.scheme,
            request.url.host,
            request.url.port,
            request.url.encodedPath,
            request.url.query,
            requestHeaders,
            requestBody,
            timestamp = requestTimestamp
        )

        if (!option.combineLog) {
            logger.log(requestLog.toLogRecord(logger.name, option.successLogLevel, logContext))
        }

        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            val responseLog = HttpResponseLog(
                requestLog,
                -1,
                exception = e,
            )
            logger.log(responseLog.toLogRecord(logger.name, option.failLogLevel, logContext))

            throw e
        }

        val responseTimestamp = Clock.System.now()
        val responseHeaders = if (option.includeResponseHeaders) response.headers.toMap() else emptyMap()
        val responseBody = capturedResponseBody(response)

        val responseLog = HttpResponseLog(
            requestLog,
            response.code,
            responseHeaders,
            responseBody,
            timestamp = responseTimestamp,
        )

        logger.log(responseLog.toLogRecord(logger.name, option.successLogLevel, logContext, option.combineLog))

        return response
    }

    private fun capturedRequestBody(request: Request): String? {
        val body = request.body

        if (body == null || !option.includeRequestBody) {
            return null
        }

        if (bodyHasUnknownEncoding(request.headers) || body.isDuplex() || body.isOneShot()) {
            return null
        }

        val declaredLength = body.contentLength()
        if (declaredLength > option.maxBodyBytes) {
            return "Skipped (${declaredLength}byte, over maxBodyBytes=${option.maxBodyBytes})"
        }

        val buffer = Buffer()
        body.writeTo(buffer)

        return buffer.loggable(option.maxBodyBytes, body.contentType()?.charset(StandardCharsets.UTF_8))
    }

    private fun capturedResponseBody(response: Response): String? {
        if (!option.includeResponseBody || !response.promisesBody()) {
            return null
        }

        if (bodyHasUnknownEncoding(response.headers)) {
            return null
        }

        val body = response.body
        if (body.contentLength() == 0L) {
            return null
        }

        val source = body.source()

        source.request(option.maxBodyBytes + 1)

        val transferred = source.buffer.clone()
        val cutShort = transferred.size > option.maxBodyBytes
        val charset = body.contentType()?.charset(StandardCharsets.UTF_8)

        if (!"gzip".equals(response.headers["Content-Encoding"], ignoreCase = true)) {
            return transferred.loggable(option.maxBodyBytes, charset, cutShort)
        }

        val decompressed = transferred.gunzipped(option.maxBodyBytes)

        return if (decompressed.size == 0L && cutShort) {
            "Unavailable (gzip body over maxBodyBytes=${option.maxBodyBytes})"
        } else {
            decompressed.loggable(option.maxBodyBytes, charset, cutShort)
        }
    }

    private fun Buffer.gunzipped(limit: Long): Buffer {
        val decompressed = Buffer()

        GzipSource(this).use { source ->
            while (decompressed.size <= limit) {
                val read = try {
                    source.read(decompressed, limit + 1 - decompressed.size)
                } catch (_: IOException) {
                    break
                }

                if (read == -1L) {
                    break
                }
            }
        }

        return decompressed
    }

    private fun Buffer.loggable(limit: Long, charset: Charset?, cutShort: Boolean = false): String {
        if (!isProbablyUtf8()) {
            return "Binary (${size}byte)"
        }

        val readable = minOf(size, limit)
        val text = clone().readString(readable, charset ?: StandardCharsets.UTF_8)

        return if (cutShort || size > limit) "$text… (truncated at ${readable}byte)" else text
    }

    private fun Buffer.isProbablyUtf8(): Boolean {
        try {
            val prefix = Buffer()
            val byteCount = size.coerceAtMost(64)
            copyTo(prefix, 0, byteCount)
            for (i in 0 until 16) {
                if (prefix.exhausted()) {
                    break
                }
                val codePoint = prefix.readUtf8CodePoint()
                if (Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint)) {
                    return false
                }
            }
            return true
        } catch (_: EOFException) {
            return false // Truncated UTF-8 sequence.
        }
    }

    private fun bodyHasUnknownEncoding(headers: Headers): Boolean {
        val contentEncoding = headers["Content-Encoding"] ?: return false
        return !contentEncoding.equals("identity", ignoreCase = true) &&
                !contentEncoding.equals("gzip", ignoreCase = true)
    }
}
