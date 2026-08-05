@file:Suppress("unused")

package kim.jade.kotlinx.logger.pipeline

import kim.jade.kotlinx.logger.LogRecord
import kotlin.math.max
import kotlin.math.min

class LogPipeline {

    internal val pipes = mutableListOf<LogPipe>()

    fun install(pipe: LogPipe): LogPipeline {
        pipe.installTo(this, pipes.size)

        return this
    }

    fun install(pipe: LogPipe, index: Int): LogPipeline {
        pipe.installTo(this, index)

        return this
    }

    fun installBefore(pipe: LogPipe, before: LogPipe.Key<out LogPipe>): LogPipeline {
        val index = pipes.indexOfFirst { it.key == before }

        pipe.installTo(this, max(0, index))

        return this
    }

    fun installAfter(pipe: LogPipe, after: LogPipe.Key<out LogPipe>): LogPipeline {
        val index = pipes.indexOfLast { it.key == after }

        pipe.installTo(this, min(index + 1, pipes.size))

        return this
    }

    fun uninstall(index: Int) {
        pipes.removeAt(index)
    }

    fun uninstall(pipe: LogPipe.Key<out LogPipe>) {
        pipes.removeAll { it.key == pipe }
    }

    fun isInstalled(pipe: LogPipe.Key<out LogPipe>): Boolean {
        return pipes.any { it.key == pipe }
    }

    fun installIndexOf(pipe: LogPipe.Key<out LogPipe>): Int {
        return pipes.indexOfFirst { it.key == pipe }
    }

    fun clear() {
        pipes.clear()
    }

    fun clone(): LogPipeline = LogPipeline().also {
        it.pipes.addAll(pipes)
    }

    fun handle(record: LogRecord) {
        var record = record

        for (pipe in pipes) {
            record = pipe.apply(record) ?: return
        }
    }
}