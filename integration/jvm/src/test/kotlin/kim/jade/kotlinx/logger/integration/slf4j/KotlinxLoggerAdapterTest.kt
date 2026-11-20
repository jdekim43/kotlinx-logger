package kim.jade.kotlinx.logger.integration.slf4j

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kim.jade.kotlinx.logger.LogLevel
import kim.jade.kotlinx.logger.LogRecord
import kim.jade.kotlinx.logger.Logger
import kim.jade.kotlinx.logger.context.CoroutineLogContext
import kim.jade.kotlinx.logger.context.CoroutineThreadLogContext
import kim.jade.kotlinx.logger.context.LogContext
import kim.jade.kotlinx.logger.context.asCoroutineLogContext
import kim.jade.kotlinx.logger.context.withLogContext
import kim.jade.kotlinx.logger.pipeline.LogPipe
import kim.jade.kotlinx.logger.pipeline.LogPipeline
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.slf4j.spi.LocationAwareLogger
import java.util.concurrent.Executors

private class Enablement(
    val slf4jLevel: String,
    val expected: Boolean,
    val probe: (KotlinxLoggerAdapter) -> Boolean,
) {
    override fun toString(): String = "$slf4jLevel is ${if (expected) "enabled" else "disabled"}"
}

class KotlinxLoggerAdapterTest : FunSpec({

    lateinit var capture: CapturingPipe
    fun adapter(name: String, level: LogLevel) =
        KotlinxLoggerAdapter(Logger(name, level, LogPipeline().install(capture)))

    beforeTest {
        capture = CapturingPipe()
    }

    context("an adapter wrapping an INFO kotlinx logger") {
        withData(
            Enablement("trace", expected = false) { it.isTraceEnabled },
            Enablement("debug", expected = false) { it.isDebugEnabled },
            Enablement("info", expected = true) { it.isInfoEnabled },
            Enablement("warn", expected = true) { it.isWarnEnabled },
            Enablement("error", expected = true) { it.isErrorEnabled },
        ) { enablement ->
            enablement.probe(adapter("level-test", LogLevel.INFO)) shouldBe enablement.expected
        }
    }

    context("message formatting") {
        test("SLF4J calls format parameters and preserve a trailing throwable") {
            val adapter = adapter("slf4j-test", LogLevel.TRACE)
            val failure = IllegalStateException("boom")

            adapter.info("hello {}, answer={}", "kotest", 42)
            adapter.error("operation {} failed", "write", failure)

            capture.records shouldHaveSize 2
            capture.records[0].run {
                loggerName shouldBe "slf4j-test"
                level shouldBe LogLevel.INFO
                body shouldBe "hello kotest, answer=42"
                exception shouldBe null
            }
            capture.records[1].run {
                level shouldBe LogLevel.ERROR
                body shouldBe "operation write failed"
                exception shouldBe failure
            }
        }

        test("LocationAwareLogger calls keep records more severe than the threshold") {
            val adapter = adapter("location-aware-severe", LogLevel.WARNING)

            adapter.log(null, "caller", LocationAwareLogger.ERROR_INT, "database down", null, null)
            adapter.log(null, "caller", LocationAwareLogger.WARN_INT, "database slow", null, null)
            adapter.log(null, "caller", LocationAwareLogger.INFO_INT, "database fine", null, null)

            capture.records.map { it.level } shouldBe listOf(LogLevel.ERROR, LogLevel.WARNING)
        }

        test("LocationAwareLogger calls normalize arguments and honor level filtering") {
            val adapter = adapter("location-aware", LogLevel.WARNING)
            val failure = IllegalArgumentException("invalid")

            adapter.log(null, "caller", LocationAwareLogger.INFO_INT, "ignored {}", arrayOf("message"), null)
            adapter.log(null, "caller", LocationAwareLogger.WARN_INT, "warning {}", arrayOf(7), failure)

            capture.records shouldHaveSize 1
            capture.records.single().run {
                level shouldBe LogLevel.WARNING
                body shouldBe "warning 7"
                exception shouldBe failure
            }
        }
    }

    context("factory and facade") {
        test("withLogContext preserves MDC writes across dispatcher switches and restores the outer scope") {
            val outer = CoroutineLogContext(mapOf("requestId" to "outer", "tenant" to "alpha"))

            withLogContext(outer) {
                MDC.get("requestId") shouldBe "outer"
                MDC.put("operation", "outer")

                val operation = withLogContext("requestId" to "inner") {
                    MDC.get("requestId") shouldBe "inner"
                    MDC.put("operation", "inner")
                    MDC.remove("requestId")

                    Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
                        withContext(dispatcher) {
                            MDC.get("requestId") shouldBe null
                            MDC.get("tenant") shouldBe "alpha"
                            MDC.get("operation")
                        }
                    }
                }

                operation shouldBe "inner"
                MDC.get("operation") shouldBe "outer"
                MDC.get("requestId") shouldBe "outer"
            }
        }

        test("withLogContext preserves the parent's MDC projection setting") {
            withContext(CoroutineThreadLogContext(includeCoroutineContext = false)) {
                val context = CoroutineLogContext(mapOf("requestId" to "request"))

                withLogContext(context) {
                    CoroutineLogContext.get()["requestId"] shouldBe "request"
                    MDC.get("requestId") shouldBe null
                }
            }
        }

        test("asCoroutineContext creates an isolated scope for MDC writes") {
            withLogContext("requestId" to "outer") {
                val inner = LogContext("requestId" to "inner")

                withContext(inner.asCoroutineLogContext()) {
                    MDC.get("requestId") shouldBe "inner"
                    MDC.put("requestId", "updated")
                }

                MDC.get("requestId") shouldBe "outer"
            }
        }

        test("SLF4J sees coroutine fields exposed through the thread context") {
            val name = "slf4j.facade.coroutine"
            val logger = Logger.named(name)
            val coroutine = CoroutineLogContext(mapOf("requestId" to "request-1"))

            try {
                logger.pipeline = LogPipeline().install(capture)
                withContext(CoroutineThreadLogContext() + coroutine) {
                    MDC.get("requestId") shouldBe "request-1"
                    LoggerFactory.getLogger(name).info("request started")
                    MDC.remove("requestId")
                    MDC.get("requestId") shouldBe null
                    coroutine["requestId"] shouldBe "request-1"
                }

                capture.records.single().context["requestId"] shouldBe "request-1"
            } finally {
                logger.resetConfiguration()
            }
        }

        test("logger factory caches adapters by name") {
            val factory = KotlinxLoggerFactory()

            val first = factory.getLogger("same")
            val second = factory.getLogger("same")
            val other = factory.getLogger("other")

            first shouldBe second
            first shouldNotBe other
        }

        test("SLF4J LoggerFactory discovers the provider and logs through the kotlinx bridge") {
            val name = "slf4j.facade.smoke"
            val kotlinxLogger = Logger.named(name)

            try {
                kotlinxLogger.level = LogLevel.TRACE
                kotlinxLogger.pipeline = LogPipeline().install(capture)

                LoggerFactory.getLogger(name).warn("facade {}", "works")

                capture.records shouldHaveSize 1
                capture.records.single().run {
                    loggerName shouldBe name
                    level shouldBe LogLevel.WARNING
                    body shouldBe "facade works"
                }
            } finally {
                kotlinxLogger.resetConfiguration()
            }
        }
    }
})

private class CapturingPipe : LogPipe {
    companion object Key : LogPipe.Key<CapturingPipe>

    override val key: LogPipe.Key<out LogPipe> = Key
    val records = mutableListOf<LogRecord>()

    override fun apply(record: LogRecord, next: (LogRecord) -> Unit) {
        records += record
        next(record)
    }
}
