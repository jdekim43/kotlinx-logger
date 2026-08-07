package kim.jade.kotlinx.logger

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kim.jade.kotlinx.logger.context.CoroutineLogContext
import kim.jade.kotlinx.logger.context.LogContext
import kim.jade.kotlinx.logger.pipeline.LogPipe
import kim.jade.kotlinx.logger.pipeline.LogPipeline
import kotlinx.coroutines.withContext

private object CapturePipeKey : LogPipe.Key<CapturePipe>

private class CapturePipe : LogPipe {
    val records = mutableListOf<LogRecord>()

    override val key: LogPipe.Key<out LogPipe> = CapturePipeKey

    override fun apply(record: LogRecord, next: (LogRecord) -> Unit) {
        records += record
        next(record)
    }
}

private class ConvenienceFunction(val level: LogLevel, val log: Logger.(String) -> Unit) {
    override fun toString(): String = "logger.${level.name.lowercase()}() emits ${level.name}"
}

class LoggerTest : FunSpec({

    lateinit var capture: CapturePipe
    fun logger(name: String, level: LogLevel) = Logger(name, level, LogPipeline().install(capture))

    beforeTest {
        capture = CapturePipe()
    }

    context("convenience functions") {
        withData(
            ConvenienceFunction(LogLevel.FATAL) { fatal(it) },
            ConvenienceFunction(LogLevel.ERROR) { error(it) },
            ConvenienceFunction(LogLevel.WARNING) { warning(it) },
            ConvenienceFunction(LogLevel.INFO) { info(it) },
            ConvenienceFunction(LogLevel.DEBUG) { debug(it) },
            ConvenienceFunction(LogLevel.TRACE) { trace(it) },
        ) { function ->
            logger("level-test", LogLevel.TRACE).run { function.log(this, "message") }

            assertSoftly(capture.records.single()) {
                level shouldBe function.level
                body shouldBe "message"
            }
        }

        test("logging arguments are copied into the emitted record") {
            val failure = IllegalStateException("failed")
            val context = LogContext(mapOf("requestId" to "request-3"))

            logger("orders", LogLevel.TRACE).warning(
                message = "inventory is low",
                exception = failure,
                eventName = "inventory",
                meta = mapOf("remaining" to 2),
                context = context,
            )

            assertSoftly(capture.records.single()) {
                loggerName shouldBe "orders"
                level shouldBe LogLevel.WARNING
                body shouldBe "inventory is low"
                exception shouldBeSameInstanceAs failure
                eventName shouldBe "inventory"
                meta shouldBe mapOf("remaining" to 2)
                this.context shouldBeSameInstanceAs context
            }
        }
    }

    context("level filtering") {
        test("records below the configured threshold never reach the pipeline") {
            val logger = logger("threshold", LogLevel.INFO)

            logger.debug("hidden")
            logger.info("visible")

            capture.records.map(LogRecord::body) shouldContainExactly listOf("visible")
        }

        test("a filtered DSL log does not evaluate its body") {
            var evaluated = false

            logger("lazy", LogLevel.WARNING).debug {
                evaluated = true
                "expensive"
            }

            evaluated shouldBe false
            capture.records.isEmpty() shouldBe true
        }
    }

    context("logging DSL") {
        test("the DSL captures properties and merges the installed coroutine context") {
            val logger = logger("coroutine", LogLevel.INFO)
            val failure = IllegalArgumentException("bad input")
            val coroutineContext = CoroutineLogContext(mapOf("jobId" to "job-4"))

            withContext(coroutineContext) {
                logger.info {
                    exception = failure
                    eventName = "accepted"
                    meta = mapOf("itemCount" to 3)
                    context = LogContext(mapOf("requestId" to "request-4"))
                    withCoroutine()
                    "request accepted"
                }
            }

            assertSoftly(capture.records.single()) {
                body shouldBe "request accepted"
                exception shouldBeSameInstanceAs failure
                eventName shouldBe "accepted"
                meta shouldBe mapOf("itemCount" to 3)
                context shouldBe mapOf("requestId" to "request-4", "jobId" to "job-4")
            }
        }
    }

    context("logging an existing record") {
        test("keeps its logger name and applies the logger threshold") {
            val logger = logger("owner", LogLevel.INFO)
            val rejected = LogRecordData("external", LogLevel.DEBUG, "hidden")
            val accepted = LogRecordData("external", LogLevel.ERROR, "visible")

            logger.log(rejected)
            logger.log(accepted)

            capture.records.single() shouldBeSameInstanceAs accepted
            capture.records.single().loggerName shouldBe "external"
        }
    }

    context("factories") {
        test("named and typed factories cache loggers by their resolved name") {
            val name = "logger-test-cached-name"

            Logger.named(name) shouldBeSameInstanceAs Logger.named(name)
            Logger.typed<LoggerTest>() shouldBeSameInstanceAs Logger.typed(LoggerTest::class)
            Logger.lazy(name).value shouldBeSameInstanceAs Logger.named(name)
        }
    }
})
