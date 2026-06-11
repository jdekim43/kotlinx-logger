package kr.jadekim.logger.integration.okhttp

import kr.jadekim.logger.JLog
import kr.jadekim.logger.JLogger
import kr.jadekim.logger.LogLevel
import kr.jadekim.logger.context.GlobalLogContext
import kr.jadekim.logger.context.LogContext
import kr.jadekim.logger.template.HttpRequestLog
import kr.jadekim.logger.template.HttpResponseLog
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.internal.http.promisesBody
import okio.Buffer
import okio.GzipSource
import java.io.EOFException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlin.time.Clock

class OkHttpLogger(
    val clientName: String,
    val option: HttpLogOption = HttpLogOption(),
    private val logger: JLogger = JLog.get("HttpClientLogger-$clientName"),
) : Interceptor {

    data class HttpLogOption(
        var successLogLevel: LogLevel = LogLevel.DEBUG,
        var failLogLevel: LogLevel = LogLevel.WARNING,
        var includeRequestHeaders: Boolean = false,
        var includeRequestBody: Boolean = false,
        var includeResponseHeaders: Boolean = false,
        var includeResponseBody: Boolean = false,
        var combineLog: Boolean = true,
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val connection = chain.connection()
        val request = chain.request()

        val logContext = GlobalLogContext.snap() + request.tag(LogContext::class.java)

        val requestHeaders = if (option.includeRequestHeaders) request.headers.toMap() else emptyMap()

        val okHttpRequestBody = request.body
        val requestBody = if (
            okHttpRequestBody == null
            || !option.includeRequestBody
            || bodyHasUnknownEncoding(request.headers)
            || okHttpRequestBody.isDuplex()
            || okHttpRequestBody.isOneShot()
        ) null else {
            val buffer = Buffer()
            okHttpRequestBody.writeTo(buffer)

            if (buffer.isProbablyUtf8()) {
                buffer.readString(Charsets.UTF_8)
            } else {
                "Binary (${request.body!!.contentLength()}byte)"
            }
        }

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
            logger.log(requestLog.toLogData(logger.name, option.successLogLevel, logContext))
        }

        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            val responseLog = HttpResponseLog(
                requestLog,
                -1,
                throwable = e,
            )
            logger.log(responseLog.toLogData(logger.name, option.failLogLevel, logContext))

            throw e
        }

        val responseTimestamp = Clock.System.now()
        val responseHeaders = if (option.includeResponseHeaders) response.headers.toMap() else emptyMap()
        val okHttpResponseBody = response.body
        val responseBody = if (
            okHttpResponseBody == null
            || !option.includeResponseBody
            || !response.promisesBody()
            || bodyHasUnknownEncoding(response.headers)
            || okHttpRequestBody!!.contentLength() == 0L
        ) null else {
            val source = okHttpResponseBody.source()
            source.request(Long.MAX_VALUE)

            var buffer = source.buffer

            if ("gzip".equals(response.headers["Content-Encoding"], ignoreCase = true)) {
                GzipSource(buffer.clone()).use { gzippedResponseBody ->
                    buffer = Buffer()
                    buffer.writeAll(gzippedResponseBody)
                }
            }

            if (buffer.isProbablyUtf8()) {
                val contentType = okHttpResponseBody.contentType()
                val charset: Charset = contentType?.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8
                buffer.clone().readString(charset)
            } else {
                "Binary (${okHttpResponseBody.contentLength()}byte)"
            }
        }

        val responseLog = HttpResponseLog(
            requestLog,
            response.code,
            responseHeaders,
            responseBody,
            timestamp = responseTimestamp,
        )

        logger.log(responseLog.toLogData(logger.name, option.successLogLevel, logContext, option.combineLog))

        return response
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