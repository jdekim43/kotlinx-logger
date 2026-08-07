package kim.jade.kotlinx.logger.pipeline

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kim.jade.kotlinx.logger.LogLevel
import kim.jade.kotlinx.logger.LogRecord
import kim.jade.kotlinx.logger.LogRecordData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout

class AsyncPipeTest : FunSpec({

    context("resuming downstream") {
        test("the first record starts processing and resumes downstream") {
            val asyncPipe = AsyncPipe()
            val record = record("first")
            val received = CompletableDeferred<LogRecord>()

            asyncPipe.apply(record) { received.complete(it) }

            withTimeout(5_000) {
                received.await() shouldBeSameInstanceAs record
            }
        }

        test("records resume through the downstream pipeline in FIFO order") {
            val asyncPipe = AsyncPipe()
            val received = Channel<String>(Channel.UNLIMITED)
            val pipeline = LogPipeline()
                .install(asyncPipe)
                .install(MapPipe { record ->
                    received.trySend(record.body).getOrThrow()
                    record
                })

            pipeline.handle(record("first"))
            pipeline.handle(record("second"))

            withTimeout(5_000) {
                received.receive() shouldBe "first"
                received.receive() shouldBe "second"
            }

            received.cancel()
        }

        test("each queued record resumes with the next callback captured for that record") {
            val asyncPipe = AsyncPipe()
            val received = Channel<String>(Channel.UNLIMITED)

            asyncPipe.apply(record("first")) { received.trySend("first:${it.body}").getOrThrow() }
            asyncPipe.apply(record("second")) { received.trySend("second:${it.body}").getOrThrow() }

            withTimeout(5_000) {
                received.receive() shouldBe "first:first"
                received.receive() shouldBe "second:second"
            }

            received.cancel()
        }
    }

    context("failure isolation") {
        test("a downstream failure does not stop later records") {
            val asyncPipe = AsyncPipe()
            val received = CompletableDeferred<String>()

            asyncPipe.apply(record("failed")) { error("boom") }
            asyncPipe.apply(record("after")) { received.complete(it.body) }

            withTimeout(5_000) {
                received.await() shouldBe "after"
            }
        }
    }
})

private fun record(body: String = "message"): LogRecordData = LogRecordData(
    loggerName = "async-pipe-test",
    level = LogLevel.INFO,
    body = body,
)
