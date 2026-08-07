package kim.jade.kotlinx.logger.integration.ktor

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.server.application.install
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kim.jade.kotlinx.logger.LogLevel
import kim.jade.kotlinx.logger.LogRecord
import kim.jade.kotlinx.logger.Logger
import kim.jade.kotlinx.logger.pipeline.LogPipe
import kim.jade.kotlinx.logger.pipeline.LogPipeline

class KtorLoggingTest : FunSpec({

    lateinit var capture: CapturePipe
    lateinit var logger: Logger

    beforeTest {
        capture = CapturePipe()
        logger = Logger(
            name = "ktor-test",
            level = LogLevel.TRACE,
            pipeline = LogPipeline().install(capture),
        )
    }

    context("RequestLogger plugin") {
        test("records request details and honors a route opt-out") {
            testApplication {
                application {
                    install(RequestLogger) {
                        this.logger = logger
                        additionalMeta = { meta ->
                            meta["requestHeader"] = request.headers["X-Test"]
                        }
                    }
                    routing {
                        get("/orders/{id}") {
                            call.respondText("ok")
                        }
                        get("/hidden") {
                            disableRequestLog()
                            call.respondText("ok")
                        }
                    }
                }

                client.get("/orders/42?tag=one&tag=two") {
                    header("X-Test", "present")
                }.status shouldBe HttpStatusCode.OK
                client.get("/hidden").status shouldBe HttpStatusCode.OK
            }

            capture.records shouldHaveSize 1
            assertSoftly(capture.records.single()) {
                level shouldBe LogLevel.INFO
                body shouldStartWith "200 OK: GET - /orders/42 in "
                meta["pathParameter"].shouldBeInstanceOf<String>() shouldContain "id = 42"
                meta["query"].shouldBeInstanceOf<String>().apply {
                    this shouldContain "tag = one"
                    this shouldContain "tag = two"
                }
                meta shouldContain ("requestHeader" to "present")
            }
        }

        test("captures readable request bodies and supports a per-route override") {
            testApplication {
                application {
                    install(RequestLogger) {
                        this.logger = logger
                        canLogBody = { true }
                    }
                    routing {
                        post("/body") {
                            call.respondText("created", status = HttpStatusCode.Created)
                        }
                        post("/private") {
                            logBody(false)
                            call.respondText("created", status = HttpStatusCode.Created)
                        }
                    }
                }

                client.post("/body") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody("""{"value":1}""")
                }.status shouldBe HttpStatusCode.Created
                client.post("/private") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody("""{"secret":true}""")
                }.status shouldBe HttpStatusCode.Created
            }

            capture.records shouldHaveSize 2
            capture.records[0].meta shouldContain ("body" to """{"value":1}""")
            capture.records[1].meta.containsKey("body") shouldBe false
        }

        test("a null configured level suppresses request logging") {
            testApplication {
                application {
                    install(RequestLogger) {
                        this.logger = logger
                        logLevel = { call -> if (call.request.path() == "/ignored") null else LogLevel.INFO }
                    }
                    routing {
                        get("/ignored") {
                            call.respondText("ok")
                        }
                    }
                }

                client.get("/ignored").status shouldBe HttpStatusCode.OK
            }

            capture.records shouldHaveSize 0
        }
    }

    context("LogContext plugin") {
        test("exposes request and custom values to logs inside a route") {
            testApplication {
                application {
                    install(LogContext) {
                        setupContext = { context ->
                            context["custom"] = "value"
                        }
                    }
                    routing {
                        get("/context/{id}") {
                            logger.info {
                                withCoroutine()
                                "inside route"
                            }
                            call.respondText("ok")
                        }
                    }
                }

                client.get("/context/7").status shouldBe HttpStatusCode.OK
            }

            capture.records shouldHaveSize 1
            assertSoftly(capture.records.single().context) {
                this["method"] shouldBe HttpMethod.Get
                this["path"] shouldBe "/context/7"
                this shouldContain ("custom" to "value")
                containsKey("route") shouldBe true
            }
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
