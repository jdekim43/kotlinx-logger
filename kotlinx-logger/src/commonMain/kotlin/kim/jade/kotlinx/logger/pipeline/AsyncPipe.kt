package kim.jade.kotlinx.logger.pipeline

import kim.jade.kotlinx.io.eprintln
import kim.jade.kotlinx.logger.LogRecord
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

@OptIn(ExperimentalAtomicApi::class)
class AsyncPipe(
    private val capacity: Int = DEFAULT_CAPACITY,
) : LogPipe {

    companion object Key : LogPipe.Key<AsyncPipe> {

        const val DEFAULT_CAPACITY: Int = 8192

        private const val DROP_REPORT_INTERVAL: Long = 1000
    }

    override val key: LogPipe.Key<out LogPipe> = Key

    private class Pending(
        val record: LogRecord,
        val next: (LogRecord) -> Unit,
    )

    private val queue = Channel<Pending>(capacity)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val isRunning = AtomicBoolean(false)
    private val droppedCount = AtomicLong(0)
    private var worker: Job? = null

    val dropped: Long
        get() = droppedCount.load()

    override fun addTo(pipeline: LogPipeline, index: Int) {
        pipeline.addPipe(this, index)
    }

    override fun apply(record: LogRecord, next: (LogRecord) -> Unit) {
        launchWorkerIfNeeded()

        enqueue(Pending(record, next))
    }

    fun shutdown() {
        queue.close()
        scope.cancel()
    }

    private fun enqueue(pending: Pending) {
        if (queue.trySend(pending).isSuccess) {
            return
        }

        if (queue.tryReceive().isSuccess) {
            reportDropped()
        }

        if (queue.trySend(pending).isFailure) {
            reportDropped()
        }
    }

    private fun launchWorkerIfNeeded() {
        if (worker?.isActive == true) {
            return
        }

        if (!isRunning.compareAndSet(false, true)) {
            return
        }

        worker = scope.launch {
            try {
                for (pending in queue) {
                    try {
                        pending.next(pending.record)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        eprintln(
                            "ERROR: AsyncPipe: An error occurred. Some logs may have been dropped.\n" +
                                    e.stackTraceToString()
                        )
                    }
                }
            } finally {
                isRunning.store(false)
            }
        }
    }

    private fun reportDropped() {
        val total = droppedCount.incrementAndFetch()

        if (total == 1L || total % DROP_REPORT_INTERVAL == 0L) {
            eprintln("WARN: AsyncPipe: queue is full (capacity=$capacity); $total records dropped so far.")
        }
    }
}
