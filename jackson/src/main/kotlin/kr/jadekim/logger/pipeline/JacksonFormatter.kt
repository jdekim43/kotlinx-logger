package kr.jadekim.logger.pipeline

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kr.jadekim.logger.Log
import kr.jadekim.logger.SerializedLog
import kr.jadekim.logger.ThrowableObjectLog
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.time.Instant

class JacksonFormatter(
    mapper: ObjectMapper = jacksonObjectMapper(),
    var traceMaxLength: Int = 12,
    useCustomDateSerializer: Boolean = false,
) : JLogPipe {

    companion object Key : JLogPipe.Key<JacksonFormatter>

    override val key: JLogPipe.Key<out JLogPipe> = Key

    private val timestampModule = SimpleModule().apply {
        addSerializer(Instant::class.java, InstantSerializer())
    }

    private val throwableModule = SimpleModule().apply {
        addSerializer(Throwable::class.java, ThrowableSerializer())
    }

    private val mapper = mapper.copy()
        .disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
        .registerModule(throwableModule)

    init {
        if (!useCustomDateSerializer) {
            mapper.registerModule(timestampModule)
        }
    }

    override fun handle(log: Log): Log = SerializedLog.LogString(log, mapper.writeValueAsString(log))

    private inner class InstantSerializer : JsonSerializer<Instant>() {

        override fun serialize(value: Instant?, gen: JsonGenerator, serializers: SerializerProvider) {
            if (value == null) {
                gen.writeNull()
                return
            }

            val timestamp = value.toEpochMilliseconds()

            gen.writeNumber(timestamp)
        }
    }

    private inner class ThrowableSerializer : JsonSerializer<Throwable>() {

        override fun serialize(value: Throwable, gen: JsonGenerator, serializers: SerializerProvider) {
            if (traceMaxLength == Int.MAX_VALUE) {
                val writer = StringWriter()
                value.printStackTrace(PrintWriter(writer))
                gen.writeString(writer.toString())
                return
            }

            val builder = StringBuilder()

            builder.appendLine(value.toString())

            value.stackTrace
                .take(traceMaxLength)
                .forEach {
                    builder.appendLine("\tat $it")
                }

            gen.writeString(builder.toString())
        }
    }

    private inner class ThrowableFieldLogSerializer : JsonSerializer<ThrowableObjectLog>() {

        override fun serialize(value: ThrowableObjectLog, gen: JsonGenerator, serializers: SerializerProvider) {
            if (value.throwable == null) {
                gen.writeNull()
                return
            }

            val fields = value.throwable!!::class.memberProperties
                .filter { it.visibility == KVisibility.PUBLIC && !it.isSuspend && !it.isLateinit && it.isAccessible }
                .associate { it.name to it.call(value.throwable) }

            gen.writeObject(fields)
        }
    }
}