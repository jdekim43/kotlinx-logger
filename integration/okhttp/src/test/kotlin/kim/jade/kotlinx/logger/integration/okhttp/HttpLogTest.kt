package kim.jade.kotlinx.logger.integration.okhttp

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import kim.jade.kotlinx.logger.LogLevel
import kim.jade.kotlinx.logger.LogRecord
import kim.jade.kotlinx.logger.Logger
import kim.jade.kotlinx.logger.context.LogContext
import kim.jade.kotlinx.logger.pipeline.LogPipe
import kim.jade.kotlinx.logger.pipeline.LogPipeline
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.DurationUnit
import kotlin.time.Instant

class HttpLogTest : FunSpec({

    lateinit var sink: CapturingPipe
    lateinit var logger: Logger

    beforeTest {
        sink = CapturingPipe()
        logger = Logger(name = "http-log-test", level = LogLevel.TRACE, pipeline = LogPipeline().install(sink))
    }

    context("HttpRequestLog") {
        test("정규화된 요청 메시지와 구조화 메타데이터를 만든다") {
            val timestamp = Instant.fromEpochMilliseconds(1_000)
            val context = LogContext(mapOf("requestId" to "req-1"))
            val failure = IllegalArgumentException("invalid request")
            val request = HttpRequestLog(
                protocol = "h2",
                method = "post",
                schema = "https",
                host = "api.example.com",
                port = 8443,
                path = "/v1/orders",
                query = "dryRun=true",
                headers = mapOf("X-Request-ID" to "req-1"),
                body = "request-body",
                exception = failure,
                timestamp = timestamp,
            )

            val record = request.toLogRecord("client", LogLevel.INFO, context)

            assertSoftly(record) {
                loggerName shouldBe "client"
                level shouldBe LogLevel.INFO
                body shouldBe "--> POST https://api.example.com:8443/v1/orders h2"
                exception shouldBe failure
                this.context shouldBe context
                meta shouldContain ("protocol" to "h2")
                meta shouldContain ("method" to "POST")
                meta shouldContain ("query" to "dryRun=true")
                meta["request"] shouldBe mapOf(
                    "headers" to mapOf("X-Request-ID" to "req-1"),
                    "body" to "request-body",
                    "timestamp" to timestamp,
                )
            }
        }
    }

    context("HttpResponseLog") {
        test("duration을 milliseconds로 기록하고 요청 포함 여부를 제어한다") {
            val request = HttpRequestLog(
                protocol = "http/1.1",
                method = "get",
                schema = "http",
                host = "localhost",
                port = 8080,
                path = "/health",
                headers = mapOf("Accept" to "application/json"),
                timestamp = Instant.fromEpochMilliseconds(1_000),
            )
            val response = HttpResponseLog(
                request = request,
                statusCode = 200,
                headers = mapOf("Content-Type" to "application/json"),
                body = "{\"ok\":true}",
                timestamp = Instant.fromEpochMilliseconds(1_250),
            )

            response.duration.toLong(DurationUnit.MILLISECONDS) shouldBe 250L

            val responseOnly = response.toLogRecord("client", LogLevel.DEBUG, withRequest = false)
            responseOnly.body shouldBe "<-- GET http://localhost:8080/health 200 (250ms)"
            responseOnly.meta["duration"] shouldBe 250L
            responseOnly.meta.containsKey("request") shouldBe false
            responseOnly.meta["response"] shouldBe mapOf(
                "headers" to mapOf("Content-Type" to "application/json"),
                "body" to "{\"ok\":true}",
                "timestamp" to Instant.fromEpochMilliseconds(1_250),
            )

            val combined = response.toLogRecord("client", LogLevel.DEBUG, withRequest = true)
            combined.meta["request"] shouldBe mapOf(
                "headers" to request.headers,
                "body" to null,
                "timestamp" to request.timestamp,
            )
        }

        test("실패 응답의 예외와 음수 status code를 보존한다") {
            val failure = IOException("network down")
            val request = HttpRequestLog(
                protocol = "",
                method = "GET",
                schema = "https",
                host = "api.example.com",
                port = 443,
                path = "/orders",
                timestamp = Instant.fromEpochMilliseconds(10),
            )
            val response = HttpResponseLog(request, statusCode = -1, exception = failure)

            val record = response.toLogRecord("client", LogLevel.WARNING)

            record.level shouldBe LogLevel.WARNING
            record.exception shouldBe failure
            record.body shouldContain " -1 ("
        }
    }

    context("공식 OkHttp 로거 어댑터") {
        context("모든 로그 레벨을 전달한다") {
            withData(
                nameFn = { "$it 레벨 로그를 전달한다" },
                LogLevel.entries - LogLevel.NONE,
            ) { level ->
                val adapter = OkHttpLogInterceptorFactory.OkHttpLoggerImpl(level, logger)

                adapter.log("message-$level")

                sink.records.shouldHaveSize(1)
                sink.records.single().level shouldBe level
                sink.records.single().body shouldBe "message-$level"
            }
        }

        test("NONE 레벨은 무시한다") {
            val adapter = OkHttpLogInterceptorFactory.OkHttpLoggerImpl(LogLevel.NONE, logger)

            adapter.log("ignored")

            sink.records.shouldBeEmpty()
        }

        test("factory는 요청한 공식 interceptor level을 설정한다") {
            val interceptor = OkHttpLogInterceptorFactory.create(
                clientName = "backend",
                logLevel = LogLevel.INFO,
                interceptLevel = HttpLoggingInterceptor.Level.HEADERS,
                logger = logger,
            )

            interceptor.level shouldBe HttpLoggingInterceptor.Level.HEADERS
        }
    }

    context("구조화 interceptor") {
        test("성공 요청과 응답의 header body context를 하나의 로그로 결합한다") {
            val interceptor = OkHttpLogger(
                clientName = "backend",
                option = OkHttpLogger.HttpLogOption(
                    successLogLevel = LogLevel.INFO,
                    includeRequestHeaders = true,
                    includeRequestBody = true,
                    includeResponseHeaders = true,
                    includeResponseBody = true,
                    combineLog = true,
                ),
                logger = logger,
            )
            val client = clientWithTerminalInterceptor(interceptor) { request ->
                successfulResponse(request)
            }
            val context = LogContext(mapOf("requestId" to "req-42"))
            val request = Request.Builder()
                .url("https://api.example.com/orders?q=kotlin")
                .header("X-Request-ID", "req-42")
                .tag(LogContext::class.java, context)
                .post("request-body".toRequestBody("text/plain; charset=utf-8".toMediaType()))
                .build()

            val responseBody = client.newCall(request).execute().use { response ->
                response.body.string()
            }

            responseBody shouldBe "response-body"
            sink.records.shouldHaveSize(1)
            val record = sink.records.single()
            record.level shouldBe LogLevel.INFO
            record.body shouldStartWith "<-- POST https://api.example.com:443/orders 201 ("
            record.context shouldBe context
            record.meta["method"] shouldBe "POST"
            record.meta["query"] shouldBe "q=kotlin"
            (record.meta["duration"] as Long).shouldBeGreaterThanOrEqual(0L)

            @Suppress("UNCHECKED_CAST")
            val requestMeta = record.meta["request"] as Map<String, Any?>
            requestMeta["body"] shouldBe "request-body"
            requestMeta["headers"] shouldBe mapOf("X-Request-ID" to "req-42")

            @Suppress("UNCHECKED_CAST")
            val responseMeta = record.meta["response"] as Map<String, Any?>
            responseMeta["body"] shouldBe "response-body"
            responseMeta["headers"] shouldBe mapOf(
                "Content-Type" to "text/plain; charset=utf-8",
                "X-Response-ID" to "res-1",
            )
        }

        test("combineLog이 false이면 요청과 응답을 분리하고 선택하지 않은 세부정보는 비운다") {
            val interceptor = OkHttpLogger(
                clientName = "backend",
                option = OkHttpLogger.HttpLogOption(
                    successLogLevel = LogLevel.DEBUG,
                    combineLog = false,
                ),
                logger = logger,
            )
            val client = clientWithTerminalInterceptor(interceptor) { request ->
                successfulResponse(request)
            }
            val request = Request.Builder()
                .url("https://api.example.com/health")
                .get()
                .build()

            val callStartedAt = Clock.System.now()
            client.newCall(request).execute().close()
            val callFinishedAt = Clock.System.now()

            sink.records.shouldHaveSize(2)
            val requestRecord = sink.records[0]
            val responseRecord = sink.records[1]

            @Suppress("UNCHECKED_CAST")
            val requestMeta = requestRecord.meta["request"] as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val responseMeta = responseRecord.meta["response"] as Map<String, Any?>
            val requestTimestamp = requestMeta["timestamp"] as Instant
            val responseTimestamp = responseMeta["timestamp"] as Instant
            val expectedDuration = responseTimestamp - requestTimestamp

            requestRecord.body shouldStartWith "--> GET https://api.example.com:443/health"
            responseRecord.body shouldBe "<-- GET https://api.example.com:443/health 201 ($expectedDuration)"
            requestMeta["headers"] shouldBe emptyMap<String, String?>()
            requestMeta["body"] shouldBe null
            responseMeta["headers"] shouldBe emptyMap<String, String?>()
            responseMeta["body"] shouldBe null
            responseRecord.meta.containsKey("request") shouldBe false
            responseRecord.meta["duration"] shouldBe expectedDuration.toLong(DurationUnit.MILLISECONDS)

            (requestTimestamp >= callStartedAt) shouldBe true
            (requestRecord.timestamp >= requestTimestamp) shouldBe true
            (responseTimestamp >= requestRecord.timestamp) shouldBe true
            (responseRecord.timestamp >= responseTimestamp) shouldBe true
            (responseRecord.timestamp <= callFinishedAt) shouldBe true
        }

        test("알 수 없는 Content-Encoding의 요청 body는 기록하지 않는다") {
            val interceptor = OkHttpLogger(
                clientName = "backend",
                option = OkHttpLogger.HttpLogOption(
                    includeRequestBody = true,
                    combineLog = true,
                ),
                logger = logger,
            )
            val client = clientWithTerminalInterceptor(interceptor) { request ->
                successfulResponse(request)
            }
            val request = Request.Builder()
                .url("https://api.example.com/compressed")
                .header("Content-Encoding", "br")
                .post("compressed".toRequestBody("text/plain".toMediaType()))
                .build()

            client.newCall(request).execute().close()

            @Suppress("UNCHECKED_CAST")
            val requestMeta = sink.records.single().meta["request"] as Map<String, Any?>
            requestMeta["body"] shouldBe null
        }

        test("chain 실패 시 failLogLevel로 한 번 기록하고 동일한 예외를 다시 던진다") {
            val interceptor = OkHttpLogger(
                clientName = "backend",
                option = OkHttpLogger.HttpLogOption(
                    failLogLevel = LogLevel.ERROR,
                    combineLog = true,
                ),
                logger = logger,
            )
            val expected = IOException("network down")
            val client = OkHttpClient.Builder()
                .addInterceptor(interceptor)
                .addInterceptor { throw expected }
                .build()
            val request = Request.Builder().url("https://api.example.com/fail").build()

            val actual = shouldThrow<IOException> {
                client.newCall(request).execute()
            }

            actual shouldBe expected
            sink.records.shouldHaveSize(1)
            sink.records.single().level shouldBe LogLevel.ERROR
            sink.records.single().exception shouldBe expected
            sink.records.single().body shouldContain " -1 ("
        }
    }
})

private class CapturingPipe : LogPipe {
    companion object Key : LogPipe.Key<CapturingPipe>

    override val key: LogPipe.Key<out LogPipe> = Key
    val records = mutableListOf<LogRecord>()

    override fun apply(record: LogRecord, next: (LogRecord) -> Unit) {
        records += record
        next(record)
    }
}

private fun clientWithTerminalInterceptor(
    interceptor: OkHttpLogger,
    response: (Request) -> Response,
) = OkHttpClient.Builder()
    .addInterceptor(interceptor)
    .addInterceptor { chain -> response(chain.request()) }
    .build()

private fun successfulResponse(request: Request): Response = Response.Builder()
    .request(request)
    .protocol(Protocol.HTTP_2)
    .code(201)
    .message("Created")
    .header("Content-Type", "text/plain; charset=utf-8")
    .header("X-Response-ID", "res-1")
    .body("response-body".toResponseBody("text/plain; charset=utf-8".toMediaType()))
    .build()
