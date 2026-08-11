package kim.jade.kotlinx.logger.pipeline

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kim.jade.kotlinx.logger.LogLevel
import kim.jade.kotlinx.logger.LogRecord
import kim.jade.kotlinx.logger.LogRecordData
import kim.jade.kotlinx.logger.context.LogContext

class RedactPipeTest : FunSpec({

    val pipe = RedactPipe()

    context("key matching") {
        withData(
            nameFn = { (key, sensitive) -> "$key -> ${if (sensitive) "redacted" else "kept"}" },
            "Authorization" to true,
            "authorization" to true,
            "X-API-KEY" to true,
            "x_api_key" to true,
            "apiKey" to true,
            "Set-Cookie" to true,
            "accessToken" to true,
            "refresh_token" to true,
            "dbPassword" to true,
            "stripeClientSecret" to true,
            "userAgent" to false,
            "requestId" to false,
            "tokenCount" to false,
            "path" to false,
        ) { (key, sensitive) ->
            pipe.isSensitive(key) shouldBe sensitive
        }
    }

    context("records") {
        test("replaces sensitive metadata and leaves the rest") {
            val redacted = pipe.redact(
                record(meta = mapOf("Authorization" to "Bearer secret-value", "status" to 200)),
            )

            redacted.meta shouldContainExactly mapOf("Authorization" to "***", "status" to 200)
        }

        test("reaches the header maps the HTTP integrations nest inside metadata") {
            val redacted = pipe.redact(
                record(
                    meta = mapOf(
                        "request" to mapOf(
                            "headers" to mapOf(
                                "Authorization" to "Bearer secret-value",
                                "Accept" to "application/json",
                            ),
                            "body" to """{"amount":1}""",
                        ),
                    ),
                ),
            )

            @Suppress("UNCHECKED_CAST")
            val request = redacted.meta["request"] as Map<String, Any?>

            assertSoftly {
                request["headers"] shouldBe mapOf(
                    "Authorization" to "***",
                    "Accept" to "application/json",
                )
                request["body"] shouldBe """{"amount":1}"""
            }
        }

        test("redacts the context, which every record of a call carries") {
            val redacted = pipe.redact(
                record(context = LogContext(mapOf("cookie" to "session=abc", "tenant" to "alpha"))),
            )

            redacted.context shouldContainExactly mapOf("cookie" to "***", "tenant" to "alpha")
        }

        test("redacts values nested in collections") {
            val redacted = pipe.redact(
                record(meta = mapOf("attempts" to listOf(mapOf("password" to "hunter2")))),
            )

            redacted.meta["attempts"] shouldBe listOf(mapOf("password" to "***"))
        }

        test("returns the same record when nothing matched") {
            val original = record(meta = mapOf("status" to 200))

            pipe.redact(original) shouldBeSameInstanceAs original
        }

        test("a self-referencing value is bounded rather than followed forever") {
            val cyclic = mutableMapOf<String, Any?>("password" to "hunter2")
            cyclic["self"] = cyclic

            val redacted = pipe.redact(record(meta = mapOf("cyclic" to cyclic)))

            redacted.meta.keys shouldBe setOf("cyclic")
        }
    }

    context("configuration") {
        test("the placeholder and key set can be replaced") {
            val custom = RedactPipe(keys = setOf("tenant"), keyPatterns = emptyList(), placeholder = "[hidden]")

            val redacted = custom.redact(
                record(meta = mapOf("tenant" to "alpha", "Authorization" to "Bearer secret-value")),
            )

            redacted.meta shouldContainExactly mapOf(
                "tenant" to "[hidden]",
                "Authorization" to "Bearer secret-value",
            )
        }
    }
})

private fun record(
    meta: Map<String, Any?> = emptyMap(),
    context: LogContext = LogContext(),
): LogRecord = LogRecordData(
    loggerName = "redact-test",
    level = LogLevel.INFO,
    body = "message",
    meta = meta,
    context = context,
)
