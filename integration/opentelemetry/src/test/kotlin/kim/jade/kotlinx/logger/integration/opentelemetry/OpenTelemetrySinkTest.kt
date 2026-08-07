package kim.jade.kotlinx.logger.integration.opentelemetry

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.KeyValue
import io.opentelemetry.api.common.Value
import io.opentelemetry.api.common.ValueType
import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.logs.LoggerBuilder
import io.opentelemetry.api.logs.LoggerProvider
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.context.Context
import kim.jade.kotlinx.logger.LogLevel
import kim.jade.kotlinx.logger.LogRecord
import kim.jade.kotlinx.logger.LogRecordData
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.time.Instant as KotlinInstant

private class AttributeExpectation(
    val key: String,
    val type: ValueType,
    val assertValue: (Value<*>) -> Unit,
) {
    override fun toString(): String = "meta[$key] becomes a $type attribute"
}

class OpenTelemetrySinkTest : FunSpec({

    context("forwarding an enabled record") {
        val provider = RecordingLoggerProvider()
        val sink = OpenTelemetrySink(provider)
        val failure = IllegalArgumentException("bad request")
        val timestamp = KotlinInstant.fromEpochMilliseconds(1_700_000_000_456L)
        val source = LogRecordData(
            loggerName = "orders",
            level = LogLevel.ERROR,
            body = "order rejected",
            exception = failure,
            eventName = "order.rejected",
            meta = mapOf(
                "text" to "value",
                "enabled" to true,
                "attempt" to 3,
                "ratio" to 1.5f,
                "missing" to null,
                "tags" to listOf("new", 7),
                "details" to mapOf("region" to "seoul"),
                "payload" to ExamplePayload(id = 9, accepted = false),
            ),
            timestamp = timestamp,
        )

        var forwarded: LogRecord? = null
        sink.apply(source) { forwarded = it }

        test("passes the record downstream unchanged") {
            forwarded shouldBeSameInstanceAs source
            sink.key shouldBe OpenTelemetrySink.Key(provider)
        }

        test("resolves the OpenTelemetry logger from the record's logger name") {
            provider.requestedScope shouldBe "orders"
            provider.logger.checkedSeverity shouldBe Severity.ERROR
            provider.logger.checkedContext shouldBeSameInstanceAs Context.current()
            provider.logger.builderRequests shouldBe 1
        }

        test("emits stable record fields through the Logs bridge") {
            assertSoftly(provider.logger.record) {
                this.timestamp shouldBe Instant.ofEpochMilli(1_700_000_000_456L)
                observedTimestamp shouldBe Instant.ofEpochMilli(1_700_000_000_456L)
                context shouldBeSameInstanceAs Context.current()
                severity shouldBe Severity.ERROR
                body shouldBe "order rejected"
                throwable shouldBeSameInstanceAs failure
                eventName shouldBe "order.rejected"
                emitted shouldBe true
            }
        }

        context("meta values are converted to OpenTelemetry attributes") {
            withData(
                AttributeExpectation("text", ValueType.STRING) { it.value shouldBe "value" },
                AttributeExpectation("enabled", ValueType.BOOLEAN) { it.value shouldBe true },
                AttributeExpectation("attempt", ValueType.LONG) { it.value shouldBe 3L },
                AttributeExpectation("ratio", ValueType.DOUBLE) { it.value shouldBe 1.5 },
                AttributeExpectation("missing", ValueType.EMPTY) {},
                AttributeExpectation("tags", ValueType.ARRAY) {
                    @Suppress("UNCHECKED_CAST")
                    (it.value as List<Value<*>>).map(Value<*>::getValue)
                        .shouldContainExactly("new", 7L)
                },
                AttributeExpectation("details", ValueType.KEY_VALUE_LIST) {
                    @Suppress("UNCHECKED_CAST")
                    (it.value as List<KeyValue>)
                        .associate { entry -> entry.key to entry.value.value }
                        .shouldContainExactly(mapOf("region" to "seoul"))
                },
                AttributeExpectation("payload", ValueType.KEY_VALUE_LIST) {
                    @Suppress("UNCHECKED_CAST")
                    (it.value as List<KeyValue>)
                        .associate { entry -> entry.key to entry.value.value }
                        .shouldContainExactly(mapOf("accepted" to false, "id" to 9L))
                },
            ) { expectation ->
                val attribute = provider.logger.record.attributes.getValue(expectation.key)

                attribute.type shouldBe expectation.type
                expectation.assertValue(attribute)
            }
        }
    }

    context("forwarding a disabled record") {
        test("suppresses record building and emission") {
            val provider = RecordingLoggerProvider(enabled = false)
            val source = LogRecordData(
                loggerName = "disabled",
                level = LogLevel.DEBUG,
                body = "not emitted",
            )

            var forwarded: LogRecord? = null
            OpenTelemetrySink(provider).apply(source) { forwarded = it }

            forwarded shouldBeSameInstanceAs source
            provider.logger.checkedSeverity shouldBe Severity.DEBUG
            provider.logger.builderRequests shouldBe 0
            provider.logger.record.emitted shouldBe false
        }
    }
})

data class ExamplePayload(val id: Int, val accepted: Boolean)

private class RecordingLoggerProvider(enabled: Boolean = true) : LoggerProvider {
    var requestedScope: String? = null
    val logger = RecordingLogger(enabled)

    override fun loggerBuilder(instrumentationScopeName: String): LoggerBuilder {
        requestedScope = instrumentationScopeName

        return object : LoggerBuilder {
            override fun setSchemaUrl(schemaUrl: String): LoggerBuilder = this

            override fun setInstrumentationVersion(instrumentationScopeVersion: String): LoggerBuilder = this

            override fun build(): Logger = logger
        }
    }
}

private class RecordingLogger(private val enabled: Boolean) : Logger {
    var checkedSeverity: Severity? = null
    var checkedContext: Context? = null
    var builderRequests: Int = 0
    val record = RecordingLogRecordBuilder()

    override fun isEnabled(severity: Severity, context: Context): Boolean {
        checkedSeverity = severity
        checkedContext = context
        return enabled
    }

    override fun logRecordBuilder(): LogRecordBuilder {
        builderRequests++
        return record
    }
}

private class RecordingLogRecordBuilder : LogRecordBuilder {
    var timestamp: Instant? = null
    var observedTimestamp: Instant? = null
    var context: Context? = null
    var severity: Severity? = null
    var body: String? = null
    var throwable: Throwable? = null
    var eventName: String? = null
    var emitted: Boolean = false
    val attributes = mutableMapOf<String, Value<*>>()

    override fun setTimestamp(timestamp: Long, unit: TimeUnit): LogRecordBuilder = apply {
        this.timestamp = Instant.ofEpochMilli(unit.toMillis(timestamp))
    }

    override fun setTimestamp(instant: Instant): LogRecordBuilder = apply {
        timestamp = instant
    }

    override fun setObservedTimestamp(timestamp: Long, unit: TimeUnit): LogRecordBuilder = apply {
        observedTimestamp = Instant.ofEpochMilli(unit.toMillis(timestamp))
    }

    override fun setObservedTimestamp(instant: Instant): LogRecordBuilder = apply {
        observedTimestamp = instant
    }

    override fun setContext(context: Context): LogRecordBuilder = apply {
        this.context = context
    }

    override fun setSeverity(severity: Severity): LogRecordBuilder = apply {
        this.severity = severity
    }

    override fun setSeverityText(severityText: String): LogRecordBuilder = apply {
        severity = Severity.valueOf(severityText)
    }

    override fun setBody(body: String): LogRecordBuilder = apply {
        this.body = body
    }

    override fun <T : Any?> setAttribute(key: AttributeKey<T>, value: T?): LogRecordBuilder = apply {
        attributes[key.key] = value.asOtelValue()
    }

    override fun setAttribute(key: String, value: Value<*>): LogRecordBuilder = apply {
        attributes[key] = value
    }

    override fun setException(throwable: Throwable): LogRecordBuilder = apply {
        this.throwable = throwable
    }

    override fun setEventName(eventName: String): LogRecordBuilder = apply {
        this.eventName = eventName
    }

    override fun emit() {
        emitted = true
    }

    private fun Any?.asOtelValue(): Value<*> = when (this) {
        null -> Value.empty()
        is String -> Value.of(this)
        is Boolean -> Value.of(this)
        is Number -> Value.of(toLong())
        else -> Value.of(toString())
    }
}
