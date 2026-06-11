package kr.jadekim.logger.template

import kr.jadekim.logger.LogData
import kr.jadekim.logger.LogLevel
import kr.jadekim.logger.context.EmptyLogContext
import kr.jadekim.logger.context.LogContext
import kotlin.time.Clock
import kotlin.time.DurationUnit
import kotlin.time.Instant

data class HttpRequestLog(
    val protocol: String,
    val method: String,
    val schema: String,
    val host: String,
    val port: Int,
    val path: String,
    val query: String? = null,
    val headers: Map<String, String?> = emptyMap(),
    val body: Any? = null,
    val throwable: Throwable? = null,
    val timestamp: Instant = Clock.System.now(),
) {

    internal val urlWithoutQuery = "$schema://$host:$port$path"

    fun toLogData(
        loggerName: String,
        logLevel: LogLevel,
        context: LogContext = EmptyLogContext,
    ) = LogData(
        loggerName,
        logLevel,
        "--> ${method.uppercase()} $urlWithoutQuery $protocol",
        throwable,
        mapOf(
            "protocol" to protocol,
            "method" to method.uppercase(),
            "schema" to schema,
            "host" to host,
            "port" to port,
            "path" to path,
            "query" to query,
            "request" to mapOf(
                "headers" to headers,
                "body" to body,
                "timestamp" to timestamp,
            ),
        ),
        context,
    )
}

data class HttpResponseLog(
    val request: HttpRequestLog,
    val statusCode: Int,
    val headers: Map<String, String?> = emptyMap(),
    val body: Any? = null,
    val throwable: Throwable? = null,
    val timestamp: Instant = Clock.System.now(),
) {

    val duration = timestamp - request.timestamp

    fun toLogData(
        loggerName: String,
        logLevel: LogLevel,
        context: LogContext = EmptyLogContext,
        withRequest: Boolean = false,
    ): LogData {
        val meta = mutableMapOf<String, Any?>(
            "protocol" to request.protocol,
            "method" to request.method.uppercase(),
            "schema" to request.schema,
            "host" to request.host,
            "port" to request.port,
            "path" to request.path,
            "query" to request.query,
            "response" to mapOf(
                "headers" to headers,
                "body" to body,
                "timestamp" to timestamp,
            ),
            "duration" to duration.toLong(DurationUnit.MILLISECONDS),
        )

        if (withRequest) {
            meta["request"] = mapOf(
                "headers" to request.headers,
                "body" to request.body,
                "timestamp" to request.timestamp,
            )
        }

        return LogData(
            loggerName,
            logLevel,
            "<-- ${request.method.uppercase()} ${request.urlWithoutQuery} $statusCode ($duration)",
            throwable,
            meta,
            context,
        )
    }
}
