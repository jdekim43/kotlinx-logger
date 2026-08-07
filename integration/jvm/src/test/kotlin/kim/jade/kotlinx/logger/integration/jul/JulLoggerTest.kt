package kim.jade.kotlinx.logger.integration.jul

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kim.jade.kotlinx.logger.LogLevel
import kim.jade.kotlinx.logger.LogRecord
import kim.jade.kotlinx.logger.Logger
import kim.jade.kotlinx.logger.pipeline.LogPipe
import kim.jade.kotlinx.logger.pipeline.LogPipeline
import java.util.logging.Level
import java.util.logging.LogManager
import kotlin.time.Instant
import java.util.logging.LogRecord as JulLogRecord

class JulLoggerTest : FunSpec({

    lateinit var originalPipeline: LogPipeline
    lateinit var originalLevel: LogLevel
    lateinit var capture: JulCapturingPipe

    beforeTest {
        originalPipeline = Logger.pipeline
        originalLevel = Logger.level
        capture = JulCapturingPipe()
        Logger.level = LogLevel.TRACE
        Logger.pipeline = LogPipeline().install(capture)
    }

    afterTest {
        Logger.pipeline = originalPipeline
        Logger.level = originalLevel
    }

    context("record translation") {
        test("JUL records are translated into kotlinx log records") {
            val failure = IllegalStateException("jul failure")
            val julRecord = JulLogRecord(Level.WARNING, "jul message").apply {
                loggerName = "jul.bridge.test"
                thrown = failure
                parameters = arrayOf<Any>("first", 2)
                sourceClassName = "ExampleClass"
                sourceMethodName = "exampleMethod"
                sequenceNumber = 91L
                instant = java.time.Instant.ofEpochMilli(1_700_000_000_123L)
                threadID = Thread.currentThread().id.toInt()
            }

            JulLogger().publish(julRecord)

            capture.records shouldHaveSize 1
            assertSoftly(capture.records.single()) {
                loggerName shouldBe "jul.bridge.test"
                level shouldBe LogLevel.WARNING
                body shouldBe "jul message"
                exception shouldBeSameInstanceAs failure
                meta shouldBe mapOf("0" to "first", "1" to 2)
                context["threadId"] shouldBe julRecord.threadID
                context["sourceClassName"] shouldBe "ExampleClass"
                context["sourceMethodName"] shouldBe "exampleMethod"
                context["sequenceNumber"] shouldBe 91L
                threadName shouldBe Thread.currentThread().name
                timestamp shouldBe Instant.fromEpochMilliseconds(1_700_000_000_123L)
            }
        }

        test("null JUL records are ignored") {
            JulLogger().publish(null)

            capture.records shouldHaveSize 0
        }
    }

    context("level mapping") {
        withData(
            nameFn = { (julLevel, expected) -> "JUL $julLevel maps to $expected" },
            Level.OFF to LogLevel.NONE,
            Level.SEVERE to LogLevel.ERROR,
            Level.INFO to LogLevel.INFO,
            Level.FINE to LogLevel.DEBUG,
            Level.FINEST to LogLevel.TRACE,
        ) { (julLevel, expectedLevel) ->
            val name = "jul.level.${julLevel.name}"

            JulLogger().publish(JulLogRecord(julLevel, "message").apply { loggerName = name })

            capture.records.single().level shouldBe expectedLevel
        }
    }

    context("root handler installation") {
        val rootLogger = LogManager.getLogManager().getLogger("")
        lateinit var originalHandlers: List<java.util.logging.Handler>

        beforeTest {
            originalHandlers = rootLogger.handlers.toList()
        }

        afterTest {
            rootLogger.handlers.forEach(rootLogger::removeHandler)
            originalHandlers.forEach(rootLogger::addHandler)
        }

        test("java.util.logging.Logger publishes through the installed root bridge") {
            val originalRootLevel = rootLogger.level
            val facade = java.util.logging.Logger.getLogger("jul.facade.smoke")
            val originalFacadeLevel = facade.level
            val originalUseParentHandlers = facade.useParentHandlers

            try {
                rootLogger.handlers.forEach(rootLogger::removeHandler)
                rootLogger.level = Level.ALL
                JulLogger.install()
                facade.level = Level.ALL
                facade.useParentHandlers = true

                facade.warning("facade message")

                capture.records shouldHaveSize 1
                capture.records.single().run {
                    loggerName shouldBe "jul.facade.smoke"
                    level shouldBe LogLevel.WARNING
                    body shouldBe "facade message"
                }
            } finally {
                rootLogger.level = originalRootLevel
                facade.level = originalFacadeLevel
                facade.useParentHandlers = originalUseParentHandlers
            }
        }

        test("install, initializer, and uninstall manage the root JUL handler") {
            JulLogger.uninstall()
            JulLogger.isInstalled() shouldBe false

            JulLoggerInitializer().run()
            JulLogger.isInstalled() shouldBe true

            JulLogger.uninstall()
            JulLogger.isInstalled() shouldBe false
        }
    }
})

private class JulCapturingPipe : LogPipe {
    companion object Key : LogPipe.Key<JulCapturingPipe>

    override val key: LogPipe.Key<out LogPipe> = Key
    val records = mutableListOf<LogRecord>()

    override fun apply(record: LogRecord, next: (LogRecord) -> Unit) {
        records += record
        next(record)
    }
}
