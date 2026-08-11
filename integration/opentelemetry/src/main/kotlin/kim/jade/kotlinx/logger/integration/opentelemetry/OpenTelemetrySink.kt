package kim.jade.kotlinx.logger.integration.opentelemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Value
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.context.Context
import kim.jade.kotlinx.logger.LogLevel
import kim.jade.kotlinx.logger.LogRecord
import kim.jade.kotlinx.logger.pipeline.LogPipe
import kotlin.reflect.KProperty
import kotlin.reflect.full.memberProperties

class OpenTelemetrySink(
    private val otelLoggerProvider: io.opentelemetry.api.logs.LoggerProvider,
    var categorizeAttributeKey: Boolean = false,
) : LogPipe {

    private companion object {

        const val MAX_VALUE_DEPTH: Int = 10
    }

    constructor(openTelemetry: OpenTelemetry) : this(openTelemetry.logsBridge)

    data class Key(val otelLoggerProvider: io.opentelemetry.api.logs.LoggerProvider) : LogPipe.Key<OpenTelemetrySink>

    override val key: LogPipe.Key<out LogPipe> = Key(otelLoggerProvider)

    override fun apply(record: LogRecord, next: (LogRecord) -> Unit) {
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
                    if (categorizeAttributeKey) {
                        setAttribute("context", record.context.toOTelValue(0, mutableSetOf()))
                        setAttribute("meta", record.meta.toOTelValue(0, mutableSetOf()))
                    } else {
                        record.context.forEach { (key, value) ->
                            setAttribute(key, value.toOTelValue(0, mutableSetOf()))
                        }
                        record.meta.forEach { (key, value) -> setAttribute(key, value.toOTelValue(0, mutableSetOf())) }
                    }

                    record.exception?.let { setException(it) }
                    record.eventName?.let { setEventName(it) }
                }
                .emit()
        }

        next(record)
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

    private fun Any?.toOTelValue(depth: Int, visited: MutableSet<Any>): Value<*> = when (this) {
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
        is Iterable<*> -> walk(depth, visited) { nextDepth -> Value.of(map { it.toOTelValue(nextDepth, visited) }) }
        is Map<*, *> -> walk(depth, visited) { nextDepth ->
            Value.of(map { it.key.toString() to it.value.toOTelValue(nextDepth, visited) }.toMap())
        }

        else -> walk(depth, visited) { nextDepth ->
            Value.of(
                this::class.memberProperties.associate {
                    it.name to it.readOrNull(this)?.toOTelValue(nextDepth, visited)
                }
            )
        }
    }

    private fun Any.walk(depth: Int, visited: MutableSet<Any>, convert: (Int) -> Value<*>): Value<*> {
        if (depth >= MAX_VALUE_DEPTH) {
            return Value.of(toString())
        }

        if (!visited.add(IdentityKey(this))) {
            return Value.of("<recursive reference to ${this::class.simpleName}>")
        }

        return try {
            convert(depth + 1)
        } catch (e: Exception) {
            Value.of("<unavailable: ${e::class.simpleName}>")
        } finally {
            visited.remove(IdentityKey(this))
        }
    }

    private class IdentityKey(val value: Any) {

        override fun hashCode(): Int = System.identityHashCode(value)

        override fun equals(other: Any?): Boolean = other is IdentityKey && other.value === value
    }

    private fun KProperty<*>.readOrNull(instance: Any): Any? = try {
        call(instance)
    } catch (_: Exception) {
        null
    }
}
