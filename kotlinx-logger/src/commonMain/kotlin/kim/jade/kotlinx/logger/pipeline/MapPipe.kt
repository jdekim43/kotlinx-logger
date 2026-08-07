package kim.jade.kotlinx.logger.pipeline

import kim.jade.kotlinx.logger.LogRecord

class MapPipe(val transform: (LogRecord) -> LogRecord) : LogPipe {

    companion object Key : LogPipe.Key<MapPipe>

    override val key: LogPipe.Key<out LogPipe> = Key

    override fun apply(record: LogRecord, next: (LogRecord) -> Unit) {
        next(transform(record))
    }
}
