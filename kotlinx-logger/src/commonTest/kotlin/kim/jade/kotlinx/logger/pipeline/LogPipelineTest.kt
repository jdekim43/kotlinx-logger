package kim.jade.kotlinx.logger.pipeline

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kim.jade.kotlinx.logger.LogLevel
import kim.jade.kotlinx.logger.LogRecord
import kim.jade.kotlinx.logger.LogRecordData

private object FirstPipeKey : LogPipe.Key<TestPipe>
private object SecondPipeKey : LogPipe.Key<TestPipe>
private object ThirdPipeKey : LogPipe.Key<TestPipe>
private object StopPipeKey : LogPipe.Key<TestPipe>

private class TestPipe(
    override val key: LogPipe.Key<out LogPipe>,
    private val block: (LogRecord, (LogRecord) -> Unit) -> Unit = { record, next -> next(record) },
) : LogPipe {
    override fun apply(record: LogRecord, next: (LogRecord) -> Unit) {
        block(record, next)
    }
}

class LogPipelineTest : FunSpec({

    lateinit var visited: MutableList<String>
    fun visitingPipe(name: String, key: LogPipe.Key<out LogPipe>) = TestPipe(key) { record, next ->
        visited += name
        next(record)
    }

    beforeTest {
        visited = mutableListOf()
    }

    context("record flow") {
        test("records flow through pipes in installation order") {
            var captured: LogRecord? = null
            val pipeline = LogPipeline()
                .install(TestPipe(FirstPipeKey) { record, next ->
                    visited += "first"
                    next((record as LogRecordData).copy(body = "${record.body}-mapped"))
                })
                .install(TestPipe(SecondPipeKey) { record, next ->
                    visited += "second"
                    captured = record
                    next(record)
                })

            pipeline.handle(record("initial"))

            visited shouldContainExactly listOf("first", "second")
            captured?.body shouldBe "initial-mapped"
        }

        test("not calling next stops every remaining pipe") {
            val pipeline = LogPipeline()
                .install(visitingPipe("before", FirstPipeKey))
                .install(TestPipe(StopPipeKey) { _, _ ->
                    visited += "stop"
                })
                .install(visitingPipe("after", ThirdPipeKey))

            pipeline.handle(record())

            visited shouldContainExactly listOf("before", "stop")
        }

        test("code after next runs after every downstream pipe returns") {
            val pipeline = LogPipeline()
                .install(TestPipe(FirstPipeKey) { record, next ->
                    visited += "first-before"
                    next(record)
                    visited += "first-after"
                })
                .install(TestPipe(SecondPipeKey) { record, next ->
                    visited += "second-before"
                    next(record)
                    visited += "second-after"
                })

            pipeline.handle(record())

            visited shouldContainExactly listOf(
                "first-before",
                "second-before",
                "second-after",
                "first-after",
            )
        }
    }

    context("pipe installation") {
        test("installBefore and installAfter place pipes relative to an installed key") {
            val pipeline = LogPipeline()
                .install(visitingPipe("second", SecondPipeKey))
                .installBefore(visitingPipe("first", FirstPipeKey), SecondPipeKey)
                .installAfter(visitingPipe("third", ThirdPipeKey), SecondPipeKey)

            pipeline.handle(record())

            visited shouldContainExactly listOf("first", "second", "third")
            pipeline.installIndexOf(FirstPipeKey) shouldBe 0
            pipeline.installIndexOf(SecondPipeKey) shouldBe 1
            pipeline.installIndexOf(ThirdPipeKey) shouldBe 2
        }

        test("relative installs fall back to the beginning when the anchor is absent") {
            val pipeline = LogPipeline().install(TestPipe(SecondPipeKey))
            val before = TestPipe(FirstPipeKey)
            val after = TestPipe(ThirdPipeKey)

            pipeline.installBefore(before, StopPipeKey)
            pipeline.installAfter(after, StopPipeKey)

            pipeline.installIndexOf(ThirdPipeKey) shouldBe 0
            pipeline.installIndexOf(FirstPipeKey) shouldBe 1
            pipeline.installIndexOf(SecondPipeKey) shouldBe 2
        }

        test("installing the same key replaces the existing pipe") {
            val original = visitingPipe("original", FirstPipeKey)
            val replacement = visitingPipe("replacement", FirstPipeKey)
            val pipeline = LogPipeline().install(original).install(replacement)

            pipeline.handle(record())

            visited shouldContainExactly listOf("replacement")
            pipeline[FirstPipeKey].single() shouldBeSameInstanceAs replacement
            pipeline.isInstalled(FirstPipeKey) shouldBe true

            pipeline.uninstall(FirstPipeKey)
            pipeline.isInstalled(FirstPipeKey) shouldBe false
            pipeline.installIndexOf(FirstPipeKey) shouldBe -1
        }

        test("install, uninstall, and clear rebuild the execution chain") {
            val pipeline = LogPipeline().install(visitingPipe("first", FirstPipeKey))
            pipeline.handle(record())

            pipeline.install(visitingPipe("second", SecondPipeKey))
            pipeline.handle(record())

            pipeline.uninstall(FirstPipeKey)
            pipeline.handle(record())

            pipeline.clear()
            pipeline.handle(record())

            visited shouldContainExactly listOf("first", "first", "second", "second")
        }
    }

    context("chain snapshots and clones") {
        test("a deferred next keeps the chain captured when handling started") {
            lateinit var resume: (LogRecord) -> Unit
            val pipeline = LogPipeline()
                .install(TestPipe(FirstPipeKey) { _, next ->
                    resume = next
                })
                .install(visitingPipe("captured", SecondPipeKey))

            val record = record()
            pipeline.handle(record)
            pipeline.uninstall(SecondPipeKey)
            pipeline.install(visitingPipe("current", ThirdPipeKey))

            resume(record)
            pipeline.handle(record)
            resume(record)

            visited shouldContainExactly listOf("captured", "current")
        }

        test("a cloned pipeline has independent structure") {
            val first = TestPipe(FirstPipeKey)
            val original = LogPipeline().install(first)
            val clone = original.clone()

            clone.clear()

            original[FirstPipeKey].single() shouldBeSameInstanceAs first
            clone.isInstalled(FirstPipeKey) shouldBe false
        }
    }
})

private fun record(body: String = "message"): LogRecordData = LogRecordData(
    loggerName = "pipeline-test",
    level = LogLevel.INFO,
    body = body,
)
