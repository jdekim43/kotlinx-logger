@file:Suppress("unused")

package kim.jade.kotlinx.logger.pipeline

import kim.jade.kotlinx.logger.LogRecord
import kotlin.math.max
import kotlin.math.min

class LogPipeline {

    internal val pipes = mutableListOf<LogPipe>()

    private val end: (LogRecord) -> Unit = {}
    private var chain: (LogRecord) -> Unit = end

    fun install(pipe: LogPipe): LogPipeline {
        pipe.addTo(this, pipes.size)
        rebuildChain()

        return this
    }

    fun install(pipe: LogPipe, index: Int): LogPipeline {
        pipe.addTo(this, index)
        rebuildChain()

        return this
    }

    fun installBefore(pipe: LogPipe, before: LogPipe.Key<out LogPipe>): LogPipeline {
        val index = pipes.indexOfFirst { it.key == before }

        pipe.addTo(this, max(0, index))
        rebuildChain()

        return this
    }

    fun installAfter(pipe: LogPipe, after: LogPipe.Key<out LogPipe>): LogPipeline {
        val index = pipes.indexOfLast { it.key == after }

        pipe.addTo(this, min(index + 1, pipes.size))
        rebuildChain()

        return this
    }

    fun uninstall(index: Int) {
        pipes.removeAt(index)
        rebuildChain()
    }

    fun uninstall(pipe: LogPipe.Key<out LogPipe>) {
        pipes.removeAll { it.key == pipe }
        rebuildChain()
    }

    fun isInstalled(pipe: LogPipe.Key<out LogPipe>): Boolean {
        return pipes.any { it.key == pipe }
    }

    fun installIndexOf(pipe: LogPipe.Key<out LogPipe>): Int {
        return pipes.indexOfFirst { it.key == pipe }
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <T : LogPipe> get(key: LogPipe.Key<T>): List<T> {
        return pipes.filter { it.key == key } as List<T>
    }

    fun clear() {
        pipes.clear()
        rebuildChain()
    }

    fun clone(): LogPipeline = LogPipeline().also {
        it.pipes.addAll(pipes)
        it.rebuildChain()
    }

    fun handle(record: LogRecord) {
        val currentChain = chain

        currentChain(record)
    }

    internal fun rebuildChain() {
        var next = end

        for (index in pipes.indices.reversed()) {
            val pipe = pipes[index]
            val downstream = next

            next = { record -> pipe.apply(record, downstream) }
        }

        chain = next
    }
}
