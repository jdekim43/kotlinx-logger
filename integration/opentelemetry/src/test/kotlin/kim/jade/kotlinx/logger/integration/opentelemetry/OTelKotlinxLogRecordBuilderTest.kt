package kim.jade.kotlinx.logger.integration.opentelemetry

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.logs.Severity
import kim.jade.kotlinx.logger.LogLevel
import kim.jade.kotlinx.logger.LogRecord
import kim.jade.kotlinx.logger.Logger
import kim.jade.kotlinx.logger.context.ThreadLogContext
import kim.jade.kotlinx.logger.pipeline.LogPipe
import kim.jade.kotlinx.logger.pipeline.LogPipeline
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.time.Instant as KotlinInstant

class OTelKotlinxLogRecordBuilderTest : FunSpec({

    beforeTest {
        ThreadLogContext.clear()
    }

    afterTest {
        ThreadLogContext.clear()
    }

    context("record building") {
        test("log record builder translates OpenTelemetry fields into a kotlinx record") {
            val failure = IllegalStateException("otel failure")
            val logger = OTelKotlinxLogger("checkout")

            val record = logger.logRecordBuilder()
                .setObservedTimestamp(1_699_999_999_000L, TimeUnit.MILLISECONDS)
                .setTimestamp(Instant.ofEpochMilli(1_700_000_000_123L))
                .setSeverity(Severity.ERROR3)
                .setBody("payment failed")
                .setAttribute(AttributeKey.stringKey("order.id"), "order-17")
                .setAttribute(AttributeKey.longKey("attempt"), 2L)
                .setException(failure)
                .setEventName("payment.failure")
                .build()

            assertSoftly(record) {
                loggerName shouldBe "checkout"
                level shouldBe LogLevel.ERROR
                body shouldBe "payment failed"
                exception shouldBeSameInstanceAs failure
                eventName shouldBe "payment.failure"
                meta.shouldContainExactly(mapOf("order.id" to "order-17", "attempt" to 2L))
                timestamp shouldBe KotlinInstant.fromEpochMilliseconds(1_700_000_000_123L)
            }
        }

        test("observed timestamp is the fallback and unset fields receive documented defaults") {
            val record = OTelKotlinxLogger("defaults")
                .logRecordBuilder()
                .setObservedTimestamp(12_345L, TimeUnit.MILLISECONDS)
                .build()

            assertSoftly(record) {
                level shouldBe LogLevel.INFO
                body shouldBe ""
                meta shouldBe emptyMap()
                timestamp shouldBe KotlinInstant.fromEpochMilliseconds(12_345L)
            }
        }
    }

    context("severity mapping") {
        withData(
            nameFn = { (severity, expected) -> "$severity maps to $expected" },
            Severity.UNDEFINED_SEVERITY_NUMBER to LogLevel.NONE,
            Severity.TRACE4 to LogLevel.TRACE,
            Severity.DEBUG3 to LogLevel.DEBUG,
            Severity.INFO2 to LogLevel.INFO,
            Severity.WARN4 to LogLevel.WARNING,
            Severity.ERROR2 to LogLevel.ERROR,
            Severity.FATAL3 to LogLevel.FATAL,
        ) { (severity, expected) ->
            val record = OTelKotlinxLogger("severity")
                .logRecordBuilder()
                .setSeverity(severity)
                .build()

            record.level shouldBe expected
        }

        test("severity text is parsed using OpenTelemetry severity names") {
            val record = OTelKotlinxLogger("severity-text")
                .logRecordBuilder()
                .setSeverityText("WARN3")
                .build()

            record.level shouldBe LogLevel.WARNING
        }
    }

    context("emission") {
        test("emit sends the built record through the matching kotlinx logger") {
            val logger = OTelKotlinxLogger("otel.emit.test")
            val capture = OTelCapturingPipe()
            val kotlinxLogger = logger.kotlinxLogger

            try {
                kotlinxLogger.level = LogLevel.TRACE
                kotlinxLogger.pipeline = LogPipeline().install(capture)

                logger.logRecordBuilder()
                    .setSeverity(Severity.DEBUG)
                    .setBody("emitted")
                    .emit()

                capture.records shouldHaveSize 1
                capture.records.single().run {
                    loggerName shouldBe "otel.emit.test"
                    level shouldBe LogLevel.DEBUG
                    body shouldBe "emitted"
                }
            } finally {
                kotlinxLogger.resetConfiguration()
            }
        }
    }

    context("severity text") {
        test("free-form severity text is mapped without failing the caller") {
            val builder = { OTelKotlinxLogger("otel.severity.text").logRecordBuilder() }

            assertSoftly {
                builder().setSeverityText("WARN").build().level shouldBe LogLevel.WARNING
                builder().setSeverityText("warn").build().level shouldBe LogLevel.WARNING
                builder().setSeverityText("Warning").build().level shouldBe LogLevel.WARNING
                builder().setSeverityText("SEVERE").build().level shouldBe LogLevel.ERROR
                builder().setSeverityText("not a severity").build().level shouldBe LogLevel.INFO
            }
        }
    }

    context("provider builder") {
        test("carries instrumentation metadata on the logger, not on the building thread") {
            ThreadLogContext.reset()

            val logger = OTelKotlinxLoggerProvider()
                .loggerBuilder("inventory")
                .setSchemaUrl("https://example.test/schema")
                .setInstrumentationVersion("2.4.1")
                .build()

            logger.scopeName shouldBe "inventory"
            ThreadLogContext.containsKey("otel") shouldBe false
        }

        test("instrumentation metadata reaches the records that logger emits") {
            val name = "otel.scope.meta"
            val capture = OTelCapturingPipe()
            val kotlinxLogger = Logger.named(name)

            try {
                kotlinxLogger.level = LogLevel.TRACE
                kotlinxLogger.pipeline = LogPipeline().install(capture)

                OTelKotlinxLoggerProvider()
                    .loggerBuilder(name)
                    .setInstrumentationVersion("2.4.1")
                    .build()
                    .logRecordBuilder()
                    .setBody("emitted")
                    .emit()

                capture.records.single().context["otel"] shouldBe mapOf(
                    "schemaUrl" to null,
                    "scopeVersion" to "2.4.1",
                )
            } finally {
                kotlinxLogger.resetConfiguration()
            }
        }
    }
})

private class OTelCapturingPipe : LogPipe {
    companion object Key : LogPipe.Key<OTelCapturingPipe>

    override val key: LogPipe.Key<out LogPipe> = Key
    val records = mutableListOf<LogRecord>()

    override fun apply(record: LogRecord, next: (LogRecord) -> Unit) {
        records += record
        next(record)
    }
}
