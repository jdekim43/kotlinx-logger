package kim.jade.kotlinx.logger.pipeline

import kim.jade.kotlinx.logger.LogRecord
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class AsyncPipe(
    capacity: Int = Channel.UNLIMITED,
) : LogPipe {

    companion object Key : LogPipe.Key<AsyncPipe>

    override val key: LogPipe.Key<out LogPipe> = Key

    private class Pending(
        val record: LogRecord,
        val next: (LogRecord) -> Unit,
    )

    private val queue = Channel<Pending>(capacity)
    private val scope = CoroutineScope(Dispatchers.Default)
    private val isRunning = AtomicBoolean(false)
    private var worker: Job? = null

    override fun addTo(pipeline: LogPipeline, index: Int) {
        pipeline.addPipe(this, index)
    }

    override fun apply(record: LogRecord, next: (LogRecord) -> Unit) {
        if (!isRunning.load() || worker?.isActive != true) {
            launchWorker()
        }

        queue.trySend(Pending(record, next)).getOrThrow()
    }

    private fun launchWorker() {
        worker = scope.launch {
            if (!isRunning.compareAndSet(false, true)) {
                return@launch
            }

            try {
                for (pending in queue) {
                    try {
                        pending.next(pending.record)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        println("ERROR: AsyncPipe: An error occurred. Some logs may have been dropped.\n${e.stackTraceToString()}")
                    }
                }
            } finally {
                isRunning.store(false)
            }
        }
    }
}
