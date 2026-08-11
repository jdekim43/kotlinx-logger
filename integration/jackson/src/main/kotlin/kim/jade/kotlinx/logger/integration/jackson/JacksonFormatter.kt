package kim.jade.kotlinx.logger.integration.jackson

import kim.jade.kotlinx.logger.LogRecord
import kim.jade.kotlinx.logger.SerializedLog
import kim.jade.kotlinx.logger.ThrowableObject
import kim.jade.kotlinx.logger.pipeline.LogPipe
import kim.jade.kotlinx.logger.pipeline.TextFormatter
import tools.jackson.core.JsonGenerator
import tools.jackson.core.StreamWriteFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.collections.iterator
import kotlin.reflect.KProperty
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.time.Instant

class JacksonFormatter(
    mapper: ObjectMapper = jacksonObjectMapper(),
    var traceLimit: Int = 12,
    useCustomDateSerializer: Boolean = false,
) : LogPipe {

    companion object Key : LogPipe.Key<JacksonFormatter>

    override val key: LogPipe.Key<out LogPipe> = Key

    private val timestampModule = SimpleModule().apply {
        addSerializer(Instant::class.java, InstantSerializer())
    }

    private val throwableModule = SimpleModule().apply {
        addSerializer(Throwable::class.java, ThrowableSerializer())
        addSerializer(ThrowableObject::class.java, ThrowableObjectSerializer())
    }

    private val textFormatter = TextFormatter()

    private val mapper: ObjectMapper = mapper.rebuild<JsonMapper, JsonMapper.Builder>()
        .disable(StreamWriteFeature.AUTO_CLOSE_TARGET)
        .addModule(throwableModule)
        .apply {
            if (!useCustomDateSerializer) {
                addModule(timestampModule)
            }
        }
        .build()

    fun format(record: LogRecord): SerializedLog.String = try {
        SerializedLog.String(record, mapper.writeValueAsString(record))
    } catch (e: Exception) {
        fallback(record, e.message)
    } catch (_: StackOverflowError) {
        fallback(record, "the logged value is too deeply nested or refers back to itself")
    }

    private fun fallback(record: LogRecord, reason: String?): SerializedLog.String {
        val text = textFormatter.format(record)

        return SerializedLog.String(record, "ERROR: JacksonFormatter failed: $reason\n${text.serialized}")
    }

    override fun apply(record: LogRecord, next: (LogRecord) -> Unit) {
        next(format(record))
    }

    private class InstantSerializer : ValueSerializer<Instant>() {

        override fun serialize(value: Instant?, gen: JsonGenerator, serializers: SerializationContext) {
            if (value == null) {
                gen.writeNull()
                return
            }

            val timestamp = value.toEpochMilliseconds()

            gen.writeNumber(timestamp)
        }
    }

    private inner class ThrowableSerializer : ValueSerializer<Throwable>() {

        override fun serialize(value: Throwable, gen: JsonGenerator, serializers: SerializationContext) {
            if (traceLimit == Int.MAX_VALUE) {
                val writer = StringWriter()
                value.printStackTrace(PrintWriter(writer))
                gen.writeString(writer.toString())
                return
            }

            val builder = StringBuilder()

            builder.appendLine(value.toString())

            value.stackTrace
                .take(traceLimit)
                .forEach {
                    builder.appendLine("\tat $it")
                }

            gen.writeString(builder.toString())
        }
    }

    private class ThrowableObjectSerializer : ValueSerializer<ThrowableObject>() {

        override fun serialize(value: ThrowableObject, gen: JsonGenerator, serializers: SerializationContext) {
            val throwable = value.throwable
            if (throwable == null) {
                gen.writeNull()
                return
            }

            val fields = throwable::class.memberProperties
                .filter { it.visibility == KVisibility.PUBLIC && !it.isSuspend && !it.isLateinit && it.isAccessible }
                .associate { it.name to it.readOrNull(throwable) }

            gen.writeStartObject()
            for ((key, value) in fields) {
                serializers.defaultSerializeProperty(key, value, gen)
            }
            gen.writeEndObject()
        }
    }
}

private fun KProperty<*>.readOrNull(instance: Any): Any? = try {
    call(instance)
} catch (_: Exception) {
    null
}
