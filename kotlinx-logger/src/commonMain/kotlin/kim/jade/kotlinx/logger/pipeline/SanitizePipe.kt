package kim.jade.kotlinx.logger.pipeline

import kim.jade.kotlinx.logger.LogRecord
import kim.jade.kotlinx.logger.LogRecordData
import kim.jade.kotlinx.logger.context.LogContext
import kim.jade.kotlinx.logger.util.escapedForLog
import kim.jade.kotlinx.logger.util.escapedValueForLog

class SanitizePipe(
    var sanitizeMeta: Boolean = true,
    var sanitizeContext: Boolean = true,
) : LogPipe {

    companion object Key : LogPipe.Key<SanitizePipe>

    override val key: LogPipe.Key<out LogPipe> = Key

    override fun addTo(pipeline: LogPipeline, index: Int) {
        if (pipeline.isInstalled(Key)) return

        super.addTo(pipeline, index)
    }

    override fun apply(record: LogRecord, next: (LogRecord) -> Unit) {
        next(sanitize(record))
    }

    fun sanitize(record: LogRecord): LogRecord {
        if (record !is LogRecordData) {
            return record
        }

        return record.copy(
            loggerName = record.loggerName.escapedForLog(),
            body = record.body.escapedForLog(),
            eventName = record.eventName?.escapedForLog(),
            meta = if (sanitizeMeta) record.meta.escapedMap() else record.meta,
            context = if (sanitizeContext) LogContext(record.context.escapedMap()) else record.context,
        )
    }

    private fun Map<String, Any?>.escapedMap(): Map<String, Any?> = if (isEmpty()) this else {
        entries.associate { it.key.escapedForLog() to it.value.escapedValueForLog() }
    }
}
