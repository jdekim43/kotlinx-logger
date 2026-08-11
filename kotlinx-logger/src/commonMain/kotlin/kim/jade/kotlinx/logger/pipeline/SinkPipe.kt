package kim.jade.kotlinx.logger.pipeline

import kim.jade.kotlinx.logger.LogRecord

class SinkPipe(val body: (LogRecord) -> Unit) : LogPipe {

    companion object Key : LogPipe.Key<SinkPipe>

    override val key: LogPipe.Key<out LogPipe> = Key

    override fun addTo(pipeline: LogPipeline, index: Int) {
        pipeline.addPipe(this, index)
    }

    override fun apply(record: LogRecord, next: (LogRecord) -> Unit) {
        body(record)
        next(record)
    }
}
