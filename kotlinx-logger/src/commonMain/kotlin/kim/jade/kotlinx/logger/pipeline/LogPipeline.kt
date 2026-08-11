@file:Suppress("unused")

package kim.jade.kotlinx.logger.pipeline

import kim.jade.kotlinx.logger.LogRecord
import kim.jade.kotlinx.logger.Logger
import kotlin.math.max
import kotlin.math.min

class LogPipeline {

    internal val pipes = mutableListOf<LogPipe>()

    private val end: (LogRecord) -> Unit = {}
    private var chain: (LogRecord) -> Unit = end
    private var silent: Boolean = false

    fun install(pipe: LogPipe): LogPipeline {
        pipe.addTo(this, pipes.size)
        mutated()

        return this
    }

    fun install(pipe: LogPipe, index: Int): LogPipeline {
        pipe.addTo(this, index)
        mutated()

        return this
    }

    fun installBefore(pipe: LogPipe, before: LogPipe.Key<out LogPipe>): LogPipeline {
        val index = pipes.indexOfFirst { it.key == before }

        pipe.addTo(this, max(0, index))
        mutated()

        return this
    }

    fun installAfter(pipe: LogPipe, after: LogPipe.Key<out LogPipe>): LogPipeline {
        val index = pipes.indexOfLast { it.key == after }

        pipe.addTo(this, min(index + 1, pipes.size))
        mutated()

        return this
    }

    fun uninstall(index: Int) {
        pipes.removeAt(index)
        mutated()
    }

    fun uninstall(pipe: LogPipe.Key<out LogPipe>) {
        pipes.removeAll { it.key == pipe }
        mutated()
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
        mutated()
    }

    fun clone(): LogPipeline = LogPipeline().also {
        it.pipes.addAll(pipes)
        it.rebuildChain()
    }

    fun handle(record: LogRecord) {
        val currentChain = chain

        currentChain(record)
    }

    /**
     * Runs [block] against this pipeline without telling any logger to re-resolve.
     *
     * Only valid while the pipeline is still private to the caller. A logger uses it to apply
     * `configurePipelineFromParent` to a freshly cloned pipeline: the `install` calls inside that block would
     * otherwise invalidate the cache entry the logger is in the middle of building, so it would re-clone on
     * every log call — and two loggers doing so would invalidate each other forever.
     */
    internal fun silently(block: LogPipeline.() -> Unit) {
        silent = true
        try {
            block()
        } finally {
            silent = false
        }
    }

    /**
     * Rebuilds the dispatch chain and tells every logger that inherits this pipeline to re-resolve.
     *
     * Only the public mutators call this. [clone] deliberately rebuilds without invalidating: a logger cloning
     * an inherited pipeline would otherwise invalidate the cache entry it just created and re-clone on every log.
     */
    private fun mutated() {
        rebuildChain()

        if (!silent) {
            Logger.ConfigurationSnapshot.invalidate()
        }
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
