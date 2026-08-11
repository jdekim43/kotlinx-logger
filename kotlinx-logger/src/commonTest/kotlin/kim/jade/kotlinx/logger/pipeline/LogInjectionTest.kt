package kim.jade.kotlinx.logger.pipeline

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kim.jade.kotlinx.logger.LogLevel
import kim.jade.kotlinx.logger.LogRecordData
import kim.jade.kotlinx.logger.context.LogContext
import kim.jade.kotlinx.logger.util.escapedForLog
import kotlin.time.Instant

private const val ESCAPE = '\u001B'

/** What an attacker submits as a query parameter to append a second, invented record. */
private val FORGED_INPUT = "ok\n1970-01-01T00:00:00Z    INFO  auth - admin login succeeded"

class LogInjectionTest : FunSpec({

    context("escapedForLog") {
        test("escapes the characters that end a line or start a terminal sequence") {
            assertSoftly {
                "a\nb".escapedForLog() shouldBe "a\\nb"
                "a\r\nb".escapedForLog() shouldBe "a\\r\\nb"
                "a\tb".escapedForLog() shouldBe "a\\tb"
                "${ESCAPE}[31mred".escapedForLog() shouldBe "\\e[31mred"
                "a\u0000b".escapedForLog() shouldBe "a\\u0000b"
                "a\u009Bb".escapedForLog() shouldBe "a\\u009bb"
            }
        }

        test("returns the same instance when there is nothing to escape") {
            val clean = "GET /orders/42 200"

            clean.escapedForLog() shouldBeSameInstanceAs clean
        }

        test("keeps text that only looks dangerous") {
            """{"path":"C:\\tmp"}""".escapedForLog() shouldBe """{"path":"C:\\tmp"}"""
        }
    }

    context("TextFormatter") {
        test("a body carrying a line terminator cannot forge a second record") {
            val text = TextFormatter().format(record(body = FORGED_INPUT)).serialized

            assertSoftly(text) {
                this.lines() shouldBe listOf(text)
                this shouldContain "ok\\n1970-01-01"
            }
        }

        test("metadata values are escaped too") {
            val text = TextFormatter().format(record(meta = mapOf("ua" to "curl\nINFO  fake"))).serialized

            text.lines() shouldBe listOf(text)
        }

        test("escaping can be turned off for sinks that are not line-oriented") {
            val text = TextFormatter(escapeControlChars = false).format(record(body = "a\nb")).serialized

            text shouldContain "a\nb"
        }
    }

    context("SanitizePipe") {
        test("escapes the record itself so later sinks cannot be injected either") {
            val sanitized = SanitizePipe().sanitize(
                record(body = "a\nb", meta = mapOf("q" to listOf("x\ny"))),
            )

            assertSoftly(sanitized) {
                body shouldBe "a\\nb"
                meta["q"] shouldBe listOf("x\\ny")
            }
        }

        test("leaves an already serialized record alone") {
            val serialized = TextFormatter().format(record())

            SanitizePipe().sanitize(serialized) shouldBeSameInstanceAs serialized
        }
    }

    context("LoggerNameShortener") {
        test("shortens a long name with an empty leading segment instead of failing") {
            val shortened = LoggerNameShortener(preferLength = 20)
                .shorten(recordNamed(".com.example.deeply.nested.feature.PaymentService"))

            shortened.loggerName shouldBe ".c.e.d.n.f.PaymentService"
        }

        test("shortens a long name with empty inner segments instead of failing") {
            val shortened = LoggerNameShortener(preferLength = 10)
                .shorten(recordNamed("com..example..deeply..nested..PaymentService"))

            shortened.loggerName shouldBe "c..e..d..n..PaymentService"
        }
    }
})

private fun record(
    body: String = "message",
    meta: Map<String, Any?> = emptyMap(),
) = LogRecordData(
    loggerName = "injection-test",
    level = LogLevel.INFO,
    body = body,
    meta = meta,
    context = LogContext(),
    threadName = null,
    timestamp = Instant.fromEpochMilliseconds(0),
)

private fun recordNamed(name: String) = LogRecordData(
    loggerName = name,
    level = LogLevel.INFO,
    body = "message",
)
