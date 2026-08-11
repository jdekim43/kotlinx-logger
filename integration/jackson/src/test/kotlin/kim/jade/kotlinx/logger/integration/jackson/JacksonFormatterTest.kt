package kim.jade.kotlinx.logger.integration.jackson

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kim.jade.kotlinx.logger.LogLevel
import kim.jade.kotlinx.logger.LogRecordData
import kim.jade.kotlinx.logger.asLogObject
import kim.jade.kotlinx.logger.context.LogContext
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.module.SimpleModule
import tools.jackson.module.kotlin.jacksonMapperBuilder
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.time.Instant

class JacksonFormatterTest : FunSpec({

    context("기본 직렬화") {
        test("로그 레코드와 Instant를 epoch milliseconds JSON으로 직렬화한다") {
            val timestamp = Instant.fromEpochMilliseconds(1_700_000_000_123)
            val record = LogRecordData(
                loggerName = "orders",
                level = LogLevel.INFO,
                body = "order accepted",
                eventName = "order.accepted",
                meta = mapOf("orderId" to 42, "paid" to true),
                context = LogContext(mapOf("requestId" to "req-1")),
                threadName = "test-thread",
                timestamp = timestamp,
            )

            val result = JacksonFormatter().format(record)
            val json = jacksonObjectMapper().readTree(result.serialized)

            result.loggerName shouldBe record.loggerName
            result.level shouldBe record.level
            assertSoftly(json) {
                get("loggerName").asString() shouldBe "orders"
                get("level").asString() shouldBe "INFO"
                get("body").asString() shouldBe "order accepted"
                get("eventName").asString() shouldBe "order.accepted"
                get("timestamp").asLong() shouldBe timestamp.toEpochMilliseconds()
                get("meta").get("orderId").asInt() shouldBe 42
                get("meta").get("paid").asBoolean() shouldBe true
                get("context").get("requestId").asString() shouldBe "req-1"
            }
        }

        test("useCustomDateSerializer는 전달한 ObjectMapper의 Instant 직렬화기를 보존한다") {
            val timestamp = Instant.fromEpochMilliseconds(1_234)
            val instantModule = SimpleModule().apply {
                addSerializer(Instant::class.java, PrefixInstantSerializer)
            }
            val customMapper = jacksonMapperBuilder().addModule(instantModule).build()
            val record = testRecord(timestamp = timestamp)

            val customJson = jacksonObjectMapper().readTree(
                JacksonFormatter(customMapper, useCustomDateSerializer = true).format(record).serialized,
            )
            val defaultJson = jacksonObjectMapper().readTree(
                JacksonFormatter(customMapper, useCustomDateSerializer = false).format(record).serialized,
            )

            customJson["timestamp"].asString() shouldBe "instant:1234"
            defaultJson["timestamp"].asLong() shouldBe 1_234
        }
    }

    context("예외 직렬화") {
        test("traceLimit은 직렬화되는 예외 프레임 수를 제한하며 실행 중 변경할 수 있다") {
            val failure = IllegalStateException("boom").apply {
                stackTrace = arrayOf(
                    StackTraceElement("First", "call", "First.kt", 10),
                    StackTraceElement("Second", "call", "Second.kt", 20),
                )
            }
            val formatter = JacksonFormatter(traceLimit = 0)
            val record = testRecord(exception = failure)

            val withoutFrames = jacksonObjectMapper().readTree(formatter.format(record).serialized)["exception"].asString()
            withoutFrames shouldContain "java.lang.IllegalStateException: boom"
            withoutFrames shouldNotContain "First.call"

            formatter.traceLimit = 1
            val withOneFrame = jacksonObjectMapper().readTree(formatter.format(record).serialized)["exception"].asString()
            withOneFrame shouldContain "First.call(First.kt:10)"
            withOneFrame shouldNotContain "Second.call"
        }

        test("ThrowableObject는 예외를 객체로 만들고 cause를 제외하며 null을 보존한다") {
            val failure = CodedException("invalid order", 409)
            val record = testRecord(
                meta = mapOf(
                    "failure" to failure.asLogObject(),
                    "empty" to (null as Throwable?).asLogObject(),
                ),
            )

            val metaJson = jacksonObjectMapper().readTree(JacksonFormatter().format(record).serialized).get("meta")
            val failureJson = metaJson.get("failure")

            failureJson.isObject shouldBe true
            failureJson.has("cause").shouldBeFalse()
            metaJson.get("empty").isNull shouldBe true
        }
    }

    context("직렬화 실패") {
        test("Jackson 직렬화 실패는 텍스트로 대체되고 호출자에게 전파되지 않는다") {
            val brokenModule = SimpleModule().apply {
                addSerializer(BrokenValue::class.java, BrokenValueSerializer)
            }
            val mapper = jacksonMapperBuilder().addModule(brokenModule).build()
            val record = testRecord(meta = mapOf("broken" to BrokenValue))

            val formatted = JacksonFormatter(mapper).format(record)

            assertSoftly(formatted.serialized) {
                this shouldContain "ERROR: JacksonFormatter failed"
                this shouldContain "serializer exploded"
                this shouldContain record.body
            }
        }

        test("자기 자신을 참조하는 메타데이터도 로그 호출을 깨뜨리지 않는다") {
            val cyclic = mutableListOf<Any?>()
            cyclic.add(cyclic)
            val record = testRecord(meta = mapOf("cyclic" to cyclic))

            val formatted = JacksonFormatter().format(record)

            formatted.serialized shouldContain record.body
        }
    }
})

private fun testRecord(
    meta: Map<String, Any?> = emptyMap(),
    exception: Throwable? = null,
    timestamp: Instant = Instant.fromEpochMilliseconds(100),
) = LogRecordData(
    loggerName = "test",
    level = LogLevel.DEBUG,
    body = "message",
    exception = exception,
    meta = meta,
    context = LogContext(),
    threadName = null,
    timestamp = timestamp,
)

class CodedException(message: String, val code: Int) : RuntimeException(message)

private data object BrokenValue

private object PrefixInstantSerializer : ValueSerializer<Instant>() {
    override fun serialize(value: Instant, gen: JsonGenerator, serializers: SerializationContext) {
        gen.writeString("instant:${value.toEpochMilliseconds()}")
    }
}

private object BrokenValueSerializer : ValueSerializer<BrokenValue>() {
    override fun serialize(value: BrokenValue, gen: JsonGenerator, serializers: SerializationContext) {
        error("serializer exploded")
    }
}
