package kim.jade.kotlinx.logger.integration.opentelemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.context.Context
import kim.jade.kotlinx.logger.LogLevel
import kim.jade.kotlinx.logger.LogRecord
import kim.jade.kotlinx.logger.pipeline.LogPipe

class OpenTelemetrySink(private val otelLoggerProvider: io.opentelemetry.api.logs.LoggerProvider) : LogPipe {

    constructor(openTelemetry: OpenTelemetry) : this(openTelemetry.logsBridge)

    companion object Key : LogPipe.Key<OpenTelemetrySink>

    override val key: LogPipe.Key<out LogPipe> = Key

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
}