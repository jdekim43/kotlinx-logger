package kim.jade.log.integration.opentelemetry

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.context.Context
import kim.jade.log.LogLevel
import kim.jade.log.LogRecordData
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

class OTelKotlinxLogRecordBuilder(private val logger: OTelKotlinxLogger) : LogRecordBuilder {

    private var timestamp: kotlin.time.Instant? = null
    private var observedTimestamp: kotlin.time.Instant? = null
    private var context: Context? = null
    private var severity: Severity? = null
    private var body: String? = null

    private var attributes: MutableMap<AttributeKey<*>, Any?> = mutableMapOf()
    private var throwable: Throwable? = null
    private var eventName: String? = null

    override fun setTimestamp(
        timestamp: Long,
        unit: TimeUnit
    ): OTelKotlinxLogRecordBuilder {
        this.timestamp = kotlin.time.Instant.fromEpochMilliseconds(unit.toMillis(timestamp))

        return this
    }

    override fun setTimestamp(instant: Instant): OTelKotlinxLogRecordBuilder {
        this.timestamp = kotlin.time.Instant.fromEpochMilliseconds(instant.toEpochMilli())

        return this
    }

    override fun setObservedTimestamp(
        timestamp: Long,
        unit: TimeUnit
    ): OTelKotlinxLogRecordBuilder {
        this.observedTimestamp = kotlin.time.Instant.fromEpochMilliseconds(unit.toMillis(timestamp))

        return this
    }

    override fun setObservedTimestamp(instant: Instant): OTelKotlinxLogRecordBuilder {
        this.observedTimestamp = kotlin.time.Instant.fromEpochMilliseconds(instant.toEpochMilli())

        return this
    }

    override fun setContext(context: Context): OTelKotlinxLogRecordBuilder {
        this.context = context

        return this
    }

    override fun setSeverity(severity: Severity): OTelKotlinxLogRecordBuilder {
        this.severity = severity

        return this
    }

    override fun setSeverityText(severityText: String): OTelKotlinxLogRecordBuilder =
        setSeverity(Severity.valueOf(severityText))

    override fun setBody(body: String): OTelKotlinxLogRecordBuilder {
        this.body = body

        return this
    }

    override fun <T : Any?> setAttribute(key: AttributeKey<T>, value: T?): OTelKotlinxLogRecordBuilder {
        this.attributes[key] = value

        return this
    }

    override fun setException(throwable: Throwable): OTelKotlinxLogRecordBuilder {
        this.throwable = throwable

        return this
    }

    override fun setEventName(eventName: String): OTelKotlinxLogRecordBuilder {
        this.eventName = eventName

        return this
    }

    fun build(): LogRecordData = LogRecordData(
        loggerName = logger.scopeName,
        level = severity?.toKotlinxLevel() ?: LogLevel.INFO,
        body = body ?: "",
        exception = throwable,
        meta = attributes.mapKeys { it.key.key },
        eventName = eventName,
        timestamp = timestamp ?: observedTimestamp ?: Clock.System.now(),
    )

    override fun emit() {
        val record = build()

        logger.kotlinxLogger.log(record)
    }

    private fun Severity.toKotlinxLevel(): LogLevel = when (this) {
        Severity.UNDEFINED_SEVERITY_NUMBER -> LogLevel.NONE
        Severity.TRACE, Severity.TRACE2, Severity.TRACE3, Severity.TRACE4 -> LogLevel.TRACE
        Severity.DEBUG, Severity.DEBUG2, Severity.DEBUG3, Severity.DEBUG4 -> LogLevel.DEBUG
        Severity.INFO, Severity.INFO2, Severity.INFO3, Severity.INFO4 -> LogLevel.INFO
        Severity.WARN, Severity.WARN2, Severity.WARN3, Severity.WARN4 -> LogLevel.WARNING
        Severity.ERROR, Severity.ERROR2, Severity.ERROR3, Severity.ERROR4 -> LogLevel.ERROR
        Severity.FATAL, Severity.FATAL2, Severity.FATAL3, Severity.FATAL4 -> LogLevel.FATAL
    }
}