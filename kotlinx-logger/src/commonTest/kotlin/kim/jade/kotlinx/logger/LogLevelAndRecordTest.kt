package kim.jade.kotlinx.logger

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kim.jade.kotlinx.logger.context.LogContext
import kotlin.time.Instant

class LogLevelAndRecordTest : FunSpec({

    context("LogLevel") {
        context("a threshold prints its own level and every more severe level") {
            withData(
                nameFn = { "threshold $it" },
                LogLevel.entries,
            ) { threshold ->
                val printable = LogLevel.entries.filter { it.isPrintableAt(threshold) }

                printable shouldContainExactly LogLevel.entries.take(threshold.ordinal + 1)
            }
        }

        context("log level names use the public wire representation") {
            withData(
                nameFn = { (level, name) -> "$level is named $name" },
                LogLevel.NONE to "NONE",
                LogLevel.FATAL to "FATAL",
                LogLevel.ERROR to "ERROR",
                LogLevel.WARNING to "WARN",
                LogLevel.INFO to "INFO",
                LogLevel.DEBUG to "DEBUG",
                LogLevel.TRACE to "TRACE",
            ) { (level, name) ->
                level.logName shouldBe name
            }
        }
    }

    context("SerializedLog") {
        test("retains the source record and exposes the serialized value") {
            val failure = IllegalStateException("boom")
            val context = LogContext(mapOf("requestId" to "request-42"))
            val record = LogRecordData(
                loggerName = "orders",
                level = LogLevel.ERROR,
                body = "checkout failed",
                exception = failure,
                eventName = "checkout",
                meta = mapOf("orderId" to 42),
                context = context,
                threadName = "worker-1",
                timestamp = Instant.fromEpochMilliseconds(1_234),
            )

            val serialized = SerializedLog.String(record, "encoded")

            assertSoftly(serialized) {
                this.serialized shouldBe "encoded"
                loggerName shouldBe record.loggerName
                level shouldBe record.level
                body shouldBe record.body
                exception shouldBeSameInstanceAs failure
                eventName shouldBe record.eventName
                meta shouldBe record.meta
                this.context shouldBeSameInstanceAs context
                threadName shouldBe record.threadName
                timestamp shouldBe record.timestamp
            }
        }
    }

    context("LogObject") {
        test("nullable throwables can be wrapped without losing identity") {
            val failure = IllegalArgumentException("invalid")

            failure.asLogObject().throwable shouldBeSameInstanceAs failure
            null.asLogObject().throwable shouldBe null
        }
    }
})
