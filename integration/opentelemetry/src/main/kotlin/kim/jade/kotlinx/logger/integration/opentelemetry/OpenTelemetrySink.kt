package kim.jade.kotlinx.logger.integration.opentelemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Value
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.context.Context
import kim.jade.kotlinx.logger.LogLevel
import kim.jade.kotlinx.logger.LogRecord
import kim.jade.kotlinx.logger.pipeline.LogPipe
import kotlin.reflect.full.memberProperties

class OpenTelemetrySink(private val otelLoggerProvider: io.opentelemetry.api.logs.LoggerProvider) : LogPipe {

    constructor(openTelemetry: OpenTelemetry) : this(openTelemetry.logsBridge)

    data class Key(val otelLoggerProvider: io.opentelemetry.api.logs.LoggerProvider) : LogPipe.Key<OpenTelemetrySink>

    override val key: LogPipe.Key<out LogPipe> = Key(otelLoggerProvider)

    override fun apply(record: LogRecord): LogRecord {
        val otelLogger = otelLoggerProvider.get(record.loggerName)
        val severity = record.level.toOTelSeverity()
        val context = Context.current()

        if (otelLogger.isEnabled(severity, context)) {
            otelLogger.logRecordBuilder()
                .setTimestamp(java.time.Instant.ofEpochMilli(record.timestamp.toEpochMilliseconds()))
                .setObservedTimestamp(java.time.Instant.ofEpochMilli(record.timestamp.toEpochMilliseconds()))
                .setContext(context)
                .setSeverity(severity)
                .setBody(record.body)
                .apply {
                    record.meta.forEach { (key, value) -> setAttribute(key, value.toOTelValue()) }
                    record.exception?.let { setException(it) }
                    record.eventName?.let { setEventName(it) }
                }
                .emit()
        }

        return record
    }

    private fun LogLevel.toOTelSeverity(): Severity = when (this) {
        LogLevel.NONE -> Severity.UNDEFINED_SEVERITY_NUMBER
        LogLevel.FATAL -> Severity.FATAL
        LogLevel.ERROR -> Severity.ERROR
        LogLevel.WARNING -> Severity.WARN
        LogLevel.INFO -> Severity.INFO
        LogLevel.DEBUG -> Severity.DEBUG
        LogLevel.TRACE -> Severity.TRACE
    }

    private fun Any?.toOTelValue(): Value<*> = when (this) {
        null -> Value.empty()
        is String -> Value.of(this)
        is Boolean -> Value.of(this)
        is Long -> Value.of(this)
        is Int -> Value.of(toLong())
        is Short -> Value.of(toLong())
        is Byte -> Value.of(toLong())
        is Double -> Value.of(this)
        is Float -> Value.of(toDouble())
        is ByteArray -> Value.of(this)
        is List<*> -> Value.of(map { it.toOTelValue() })
        is Map<*, *> -> Value.of(map { it.key.toString() to it.value.toOTelValue() }.toMap())
        else -> Value.of(this::class.memberProperties.associate { it.name to it.call(this)?.toOTelValue() })
    }
}