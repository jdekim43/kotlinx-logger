package kim.jade.kotlinx.logger.integration.sentry

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import kim.jade.kotlinx.logger.LogLevel
import kim.jade.kotlinx.logger.LogRecord
import kim.jade.kotlinx.logger.LogRecordData
import kim.jade.kotlinx.logger.context.LogContext

class SentrySinkTest : FunSpec({
    val capturedEvents = mutableListOf<SentryEvent>()

    beforeTest {
        capturedEvents.clear()
        Sentry.close()
        Sentry.init { options ->
            options.dsn = "https://public@example.com/1"
            options.isEnableExternalConfiguration = false
            options.isEnableUncaughtExceptionHandler = false
            options.isEnableShutdownHook = false
            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
                capturedEvents += event
                null
            }
        }
    }

    afterTest {
        Sentry.close()
    }

    context("event conversion") {
        test("an accepted record is converted to a structured Sentry event") {
            val exception = IllegalStateException("broken")
            val record = LogRecordData(
                loggerName = "checkout",
                level = LogLevel.ERROR,
                body = "payment failed",
                exception = exception,
                eventName = "payment.failed",
                meta = mapOf("paymentId" to 42, "nullable" to null),
                context = LogContext(mapOf("tenant" to "acme", "ignored" to null)),
            )

            var forwarded: LogRecord? = null
            SentrySink { true }.apply(record) { forwarded = it }

            forwarded shouldBeSameInstanceAs record
            capturedEvents.size shouldBe 1
            assertSoftly(capturedEvents.single()) {
                logger shouldBe "checkout"
                level shouldBe SentryLevel.ERROR
                message?.message shouldBe "payment failed"
                message?.params shouldContainExactly listOf("paymentId - 42", "nullable - null")
                throwable shouldBeSameInstanceAs exception
                contexts["tenant"] shouldBe "acme"
                contexts.containsKey("ignored") shouldBe false
                extras.orEmpty() shouldContain ("paymentId" to 42)
                extras.orEmpty().containsKey("nullable") shouldBe false
            }
        }
    }

    context("record filtering") {
        test("the default predicate only accepts warning and more severe records") {
            val sink = SentrySink()
            val forwarded = mutableListOf<LogRecord>()

            sink.apply(recordAt(LogLevel.INFO)) { forwarded += it }
            sink.apply(recordAt(LogLevel.WARNING)) { forwarded += it }
            sink.apply(recordAt(LogLevel.ERROR)) { forwarded += it }
            sink.apply(recordAt(LogLevel.FATAL)) { forwarded += it }

            capturedEvents.map { it.level } shouldContainExactly listOf(
                SentryLevel.WARNING,
                SentryLevel.ERROR,
                SentryLevel.FATAL,
            )
            forwarded.map { it.level } shouldContainExactly listOf(
                LogLevel.INFO,
                LogLevel.WARNING,
                LogLevel.ERROR,
                LogLevel.FATAL,
            )
        }
    }

    context("level mapping") {
        withData(
            nameFn = { (level, sentryLevel) -> "$level maps to Sentry $sentryLevel" },
            LogLevel.NONE to SentryLevel.DEBUG,
            LogLevel.FATAL to SentryLevel.FATAL,
            LogLevel.ERROR to SentryLevel.ERROR,
            LogLevel.WARNING to SentryLevel.WARNING,
            LogLevel.INFO to SentryLevel.INFO,
            LogLevel.DEBUG to SentryLevel.DEBUG,
            LogLevel.TRACE to SentryLevel.DEBUG,
        ) { (level, sentryLevel) ->
            SentrySink { true }.apply(recordAt(level)) {}

            capturedEvents.single().level shouldBe sentryLevel
        }
    }
})

private fun recordAt(level: LogLevel) = LogRecordData(
    loggerName = "test",
    level = level,
    body = level.logName,
)
