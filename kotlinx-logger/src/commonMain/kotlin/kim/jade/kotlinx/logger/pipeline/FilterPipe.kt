package kim.jade.kotlinx.logger.pipeline

import kim.jade.kotlinx.logger.LogRecord

class FilterPipe(val predicate: (LogRecord) -> Boolean) : LogPipe {

    companion object Key : LogPipe.Key<FilterPipe>

    override val key: LogPipe.Key<out LogPipe> = Key

    override fun addTo(pipeline: LogPipeline, index: Int) {
        pipeline.addPipe(this, index)
    }

    override fun apply(record: LogRecord, next: (LogRecord) -> Unit) {
        if (predicate(record)) {
            next(record)
        }
    }
}
