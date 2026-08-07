package kim.jade.kotlinx.logger.pipeline

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kim.jade.kotlinx.logger.LogLevel
import kim.jade.kotlinx.logger.LogRecordData
import kim.jade.kotlinx.logger.SerializedLog
import kim.jade.kotlinx.logger.context.LogContext
import kotlin.time.Instant

class FormatterAndShortenerTest : FunSpec({

    context("TextFormatter") {
        test("renders stable record fields, event name, and metadata") {
            val record = LogRecordData(
                loggerName = "payments.Service",
                level = LogLevel.WARNING,
                body = "payment delayed",
                eventName = "retry",
                meta = linkedMapOf("attempt" to 2, "urgent" to true),
                context = LogContext(mapOf("secret-context" to "not-rendered")),
                threadName = "worker-1",
                timestamp = Instant.fromEpochMilliseconds(0),
            )

            val formatted = TextFormatter(printMeta = true, enableColor = false).format(record)

            formatted.serialized shouldStartWith "1970-01-01T00:00:00Z    [worker-1] WARN  "
            formatted.serialized shouldContain "payments.Service".padEnd(36)
            formatted.serialized shouldContain " - #retry payment delayed (attempt=2, urgent=true)"
            formatted.serialized shouldNotContain "secret-context"
            formatted.body shouldBe record.body
        }

        test("omits optional thread, event, and metadata sections") {
            val record = LogRecordData(
                loggerName = "health",
                level = LogLevel.INFO,
                body = "ready",
                meta = mapOf("ignored" to true),
                threadName = null,
                timestamp = Instant.fromEpochMilliseconds(0),
            )

            val text = TextFormatter(printMeta = false, enableColor = false).format(record).serialized

            text shouldBe "1970-01-01T00:00:00Z    INFO  ${"health".padEnd(36)} - ready"
        }
    }

    context("LoggerNameShortener") {
        test("shortens leading segments until the preferred length is met") {
            val shortener = LoggerNameShortener(preferLength = 20)
            val record = recordNamed("com.example.feature.PaymentService")

            val shortened = shortener.shorten(record)

            shortened.loggerName shouldBe "c.e.f.PaymentService"
            record.loggerName shouldBe "com.example.feature.PaymentService"
        }

        context("simple-name mode keeps only the last segment") {
            withData(
                nameFn = { (source, expected) -> "$source -> $expected" },
                "com.example.PaymentService" to "PaymentService",
                "PaymentService" to "PaymentService",
            ) { (source, expected) ->
                LoggerNameShortener(useSimpleName = true).shorten(recordNamed(source)).loggerName shouldBe expected
            }
        }

        test("does not unwrap or replace an already serialized record") {
            val serialized = SerializedLog.String(recordNamed("com.example.PaymentService"), "encoded")

            LoggerNameShortener(preferLength = 1).shorten(serialized) shouldBeSameInstanceAs serialized
        }

        test("only the first logger-name shortener is retained in a pipeline") {
            val first = LoggerNameShortener(preferLength = 20)
            val second = LoggerNameShortener(useSimpleName = true)
            val pipeline = LogPipeline().install(first).install(second)

            pipeline[LoggerNameShortener].single() shouldBeSameInstanceAs first
        }
    }
})

private fun recordNamed(name: String): LogRecordData = LogRecordData(
    loggerName = name,
    level = LogLevel.INFO,
    body = "message",
)
