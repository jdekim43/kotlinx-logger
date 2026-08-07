package kim.jade.kotlinx.logger.integration.koin

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import kim.jade.kotlinx.logger.LogLevel
import kim.jade.kotlinx.logger.LogRecord
import kim.jade.kotlinx.logger.Logger
import kim.jade.kotlinx.logger.pipeline.LogPipe
import kim.jade.kotlinx.logger.pipeline.LogPipeline
import org.koin.core.logger.Level

class KoinLoggerTest : FunSpec({

    lateinit var capture: CapturePipe
    lateinit var logger: Logger

    beforeTest {
        capture = CapturePipe()
        logger = Logger(
            name = "koin-test",
            level = LogLevel.TRACE,
            pipeline = LogPipeline().install(capture),
        )
    }

    context("Koin log levels are forwarded to the matching kotlinx logger levels") {
        withData(
            nameFn = { (koinLevel, kotlinxLevel) -> "Koin $koinLevel maps to $kotlinxLevel" },
            Level.DEBUG to LogLevel.DEBUG,
            Level.INFO to LogLevel.INFO,
            Level.WARNING to LogLevel.WARNING,
            Level.ERROR to LogLevel.ERROR,
        ) { (koinLevel, kotlinxLevel) ->
            KoinLogger(logger).display(koinLevel, "message-$koinLevel")

            val record = capture.records.single()
            record.level shouldBe kotlinxLevel
            record.body shouldBe "message-$koinLevel"
            record.loggerName shouldBe "koin-test"
        }
    }

    context("NONE") {
        test("does not emit a log record") {
            KoinLogger(logger).display(Level.NONE, "ignored")

            capture.records shouldBe emptyList()
        }
    }
})

private class CapturePipe : LogPipe {
    companion object Key : LogPipe.Key<CapturePipe>

    override val key: LogPipe.Key<out LogPipe> = Key
    val records = mutableListOf<LogRecord>()

    override fun apply(record: LogRecord, next: (LogRecord) -> Unit) {
        records += record
        next(record)
    }
}
