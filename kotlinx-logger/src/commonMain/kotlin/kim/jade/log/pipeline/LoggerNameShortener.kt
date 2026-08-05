package kim.jade.log.pipeline

import kim.jade.log.LogRecord
import kim.jade.log.LogRecordData

open class LoggerNameShortener(var preferLength: Int = 36) : LogPipe {

    companion object Key : LogPipe.Key<LoggerNameShortener>

    override val key: LogPipe.Key<out LogPipe> = Key

    override fun installTo(pipeline: LogPipeline, index: Int) {
        if (pipeline.isInstalled(Key)) return

        super.installTo(pipeline, index)
    }

    override fun apply(record: LogRecord): LogRecord? {
        if (record is LogRecordData) {
            return record.copy(loggerName = transform(record.loggerName))
        }

        return record
    }

    protected fun transform(name: String): String {
        val result = mutableListOf<String>()
        val tokens = name.split(".")

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