package kim.jade.log.pipeline

import kim.jade.log.LogRecord

class FilterPipe(val predicate: (LogRecord) -> Boolean) : LogPipe {

    companion object Key : LogPipe.Key<FilterPipe>

    override val key: LogPipe.Key<out LogPipe> = Key

    override fun apply(record: LogRecord): LogRecord? = if (predicate(record)) record else null
}
