package kim.jade.log.pipeline

import kim.jade.log.LogRecord

class MapPipe(val transform: (LogRecord) -> LogRecord) : LogPipe {

    companion object Key : LogPipe.Key<MapPipe>

    override val key: LogPipe.Key<out LogPipe> = Key

    override fun apply(record: LogRecord): LogRecord = transform(record)
}