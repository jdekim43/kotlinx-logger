package kim.jade.kotlinx.logger.pipeline

import kim.jade.kotlinx.logger.LogRecord

interface LogPipe {

    interface Key<P : LogPipe>

    val key: Key<out LogPipe>

    fun addTo(pipeline: LogPipeline, index: Int) {
        val indexes = pipeline.pipes.withIndex().filter { it.value.key == key }.map { it.index }
        val newIndex = index - indexes.count { it <= index }
        pipeline.uninstall(key)
        pipeline.addPipe(this, newIndex)
    }

    fun apply(record: LogRecord, next: (LogRecord) -> Unit)

    fun LogPipeline.addPipe(pipe: LogPipe, index: Int) {
        pipes.add(index, pipe)
    }
}
