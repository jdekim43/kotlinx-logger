package kim.jade.kotlinx.logger.integration.slf4j

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kim.jade.kotlinx.logger.LogLevel
import kim.jade.kotlinx.logger.LogRecord
import kim.jade.kotlinx.logger.Logger
import kim.jade.kotlinx.logger.pipeline.LogPipe
import kim.jade.kotlinx.logger.pipeline.LogPipeline
import org.slf4j.LoggerFactory
import org.slf4j.spi.LocationAwareLogger

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
            val originalLevel = kotlinxLogger.level
            val originalPipeline = kotlinxLogger.pipeline

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
                kotlinxLogger.level = originalLevel
                kotlinxLogger.pipeline = originalPipeline
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
