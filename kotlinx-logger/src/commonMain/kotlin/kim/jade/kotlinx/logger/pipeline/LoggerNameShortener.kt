package kim.jade.kotlinx.logger.pipeline

import kim.jade.kotlinx.logger.LogRecord
import kim.jade.kotlinx.logger.LogRecordData

open class LoggerNameShortener(
    var preferLength: Int = 36,
    var useSimpleName: Boolean = false,
) : LogPipe {

    companion object Key : LogPipe.Key<LoggerNameShortener>

    override val key: LogPipe.Key<out LogPipe> = Key

    override fun installTo(pipeline: LogPipeline, index: Int) {
        if (pipeline.isInstalled(Key)) return

        super.installTo(pipeline, index)
    }

    override fun apply(record: LogRecord): LogRecord {
        if (record is LogRecordData) {
            return record.copy(loggerName = transform(record.loggerName))
        }

        return record
    }

    protected fun transform(name: String): String {
        if (useSimpleName) {
            return name.substringAfterLast('.')
        }

        val result = mutableListOf<String>()
        val tokens = name.split('.')

        if (tokens.size < 2) {
            return name
        }

        var diff = name.length - preferLength
        var idx = 0
        while (diff > 0 && tokens.size - 1 > idx) {
            val token = tokens[idx]
            result.add(token[0].toString())
            diff -= token.length - 1
            idx++
        }

        while (tokens.size > idx) {
            result.add(tokens[idx])
            idx++
        }

        return result.joinToString(".")
    }
}