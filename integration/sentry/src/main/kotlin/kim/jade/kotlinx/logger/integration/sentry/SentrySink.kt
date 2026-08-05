package kim.jade.kotlinx.logger.integration.sentry

import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.protocol.Message
import kim.jade.kotlinx.logger.LogLevel
import kim.jade.kotlinx.logger.LogRecord
import kim.jade.kotlinx.logger.pipeline.LogPipe

class SentrySink(
    val isAcceptable: (record: LogRecord) -> Boolean = { it.level.isPrintableAt(LogLevel.WARNING) },
) : LogPipe {

    companion object : LogPipe.Key<SentrySink>

    override val key: LogPipe.Key<out LogPipe> = SentrySink

    override fun apply(record: LogRecord): LogRecord {
        if (!isAcceptable(record)) {
            return record
        }

        val event = SentryEvent().apply {
            logger = record.loggerName
            level = record.level.toSentryLevel()
            message = Message().apply {
                message = record.body
                params = record.meta.map { "${it.key} - ${it.value}" }
            }
            throwable = record.exception
            contexts.putAll(record.context.filterValues { it != null })
            record.meta.filterValues { it != null }.forEach { (key, value) -> if (value != null) setExtra(key, value) }
        }

        Sentry.captureEvent(event)
        return record
    }

    private fun LogLevel.toSentryLevel(): SentryLevel =
        when (this) {
            LogLevel.NONE -> SentryLevel.DEBUG
            LogLevel.TRACE -> SentryLevel.DEBUG
            LogLevel.DEBUG -> SentryLevel.DEBUG
            LogLevel.INFO -> SentryLevel.INFO
            LogLevel.WARNING -> SentryLevel.WARNING
            LogLevel.ERROR -> SentryLevel.ERROR
            LogLevel.FATAL -> SentryLevel.FATAL
        }
}
