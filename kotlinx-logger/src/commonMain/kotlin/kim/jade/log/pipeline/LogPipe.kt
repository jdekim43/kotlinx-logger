package kim.jade.log.pipeline

import kim.jade.log.LogRecord

interface LogPipe {

    interface Key<P : LogPipe>

    val key: Key<out LogPipe>

    fun installTo(pipeline: LogPipeline, index: Int) {
        pipeline.addPipe(this, index)
    }

    fun apply(record: LogRecord): LogRecord?

    fun LogPipeline.addPipe(pipe: LogPipe, index: Int) {
        pipes.add(index, pipe)
    }
}