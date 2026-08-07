package kim.jade.kotlinx.logger.integration.gson

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import kim.jade.kotlinx.logger.LogLevel
import kim.jade.kotlinx.logger.LogRecordData
import kim.jade.kotlinx.logger.asLogObject
import kim.jade.kotlinx.logger.context.LogContext
import kotlin.time.Instant

class GsonFormatterTest : FunSpec({

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

            val result = GsonFormatter().format(record)
            val json = JsonParser.parseString(result.serialized).asJsonObject

            result.loggerName shouldBe record.loggerName
            result.level shouldBe record.level
            assertSoftly(json) {
                get("loggerName").asString shouldBe "orders"
                get("level").asString shouldBe "INFO"
                get("body").asString shouldBe "order accepted"
                get("eventName").asString shouldBe "order.accepted"
                get("timestamp").asLong shouldBe timestamp.toEpochMilliseconds()
                get("meta").asJsonObject["orderId"].asInt shouldBe 42
                get("meta").asJsonObject["paid"].asBoolean shouldBe true
                get("context").asJsonObject["requestId"].asString shouldBe "req-1"
            }
        }

        test("useCustomDateSerializer는 전달한 Gson의 Instant 직렬화기를 보존한다") {
            val timestamp = Instant.fromEpochMilliseconds(1_234)
            val customGson = GsonBuilder()
                .registerTypeAdapter(
                    Instant::class.java,
                    JsonSerializer<Instant> { value, _, _ -> JsonPrimitive("instant:${value.toEpochMilliseconds()}") },
                )
                .create()
            val record = testRecord(timestamp = timestamp)

            val customJson = JsonParser.parseString(
                GsonFormatter(customGson, useCustomDateSerializer = true).format(record).serialized,
            ).asJsonObject
            val defaultJson = JsonParser.parseString(
                GsonFormatter(customGson, useCustomDateSerializer = false).format(record).serialized,
            ).asJsonObject

            customJson["timestamp"].asString shouldBe "instant:1234"
            defaultJson["timestamp"].asLong shouldBe 1_234
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
            val formatter = GsonFormatter(traceLimit = 0)
            val record = testRecord(exception = failure)

            val withoutFrames = JsonParser.parseString(formatter.format(record).serialized)
                .asJsonObject["exception"].asString
            withoutFrames shouldContain "java.lang.IllegalStateException: boom"
            withoutFrames shouldNotContain "First.call"

            formatter.traceLimit = 1
            val withOneFrame = JsonParser.parseString(formatter.format(record).serialized)
                .asJsonObject["exception"].asString
            withOneFrame shouldContain "First.call(First.kt:10)"
            withOneFrame shouldNotContain "Second.call"
        }

        test("ThrowableObject는 공개 예외 속성을 객체로 만들고 cause는 제외한다") {
            val failure = CodedException("invalid order", 409)
            val record = testRecord(meta = mapOf("failure" to failure.asLogObject()))

            val failureJson = JsonParser.parseString(GsonFormatter().format(record).serialized)
                .asJsonObject["meta"].asJsonObject["failure"].asJsonObject

            failureJson["code"].asInt shouldBe 409
            failureJson.has("cause").shouldBeFalse()
        }
    }

    context("직렬화 실패") {
        test("Gson 직렬화 실패 시 오류 설명과 텍스트 포맷 결과로 폴백한다") {
            val brokenGson = GsonBuilder()
                .registerTypeAdapter(
                    BrokenValue::class.java,
                    JsonSerializer<BrokenValue> { _, _, _ -> error("serializer exploded") },
                )
                .create()
            val record = testRecord(body = "fallback body", meta = mapOf("broken" to BrokenValue))

            val result = GsonFormatter(brokenGson).format(record)

            result.serialized shouldStartWith "ERROR: GsonFormatter failed:"
            result.serialized shouldContain "serializer exploded"
            result.serialized shouldContain "fallback body"
            result.loggerName shouldBe record.loggerName
        }
    }
})

private fun testRecord(
    body: String = "message",
    meta: Map<String, Any?> = emptyMap(),
    exception: Throwable? = null,
    timestamp: Instant = Instant.fromEpochMilliseconds(100),
) = LogRecordData(
    loggerName = "test",
    level = LogLevel.DEBUG,
    body = body,
    exception = exception,
    meta = meta,
    context = LogContext(),
    threadName = null,
    timestamp = timestamp,
)

class CodedException(message: String, val code: Int) : RuntimeException(message)

private data object BrokenValue
