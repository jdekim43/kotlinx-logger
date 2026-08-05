package kim.jade.kotlinx.logger.integration.gson

import com.google.gson.*
import kim.jade.kotlinx.logger.LogRecord
import kim.jade.kotlinx.logger.SerializedLog
import kim.jade.kotlinx.logger.ThrowableObject
import kim.jade.kotlinx.logger.pipeline.LogPipe
import kim.jade.kotlinx.logger.pipeline.TextFormatter
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.Type
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties
import kotlin.time.Instant

class GsonFormatter(
    gson: Gson = Gson(),
    var traceLimit: Int = 12,
    useCustomDateSerializer: Boolean = false,
) : kim.jade.kotlinx.logger.pipeline.LogPipe {

    companion object Key : LogPipe.Key<GsonFormatter>

    override val key: LogPipe.Key<out LogPipe> = Key

    private val gson = gson.newBuilder()
        .registerTypeHierarchyAdapter(Throwable::class.java, ThrowableSerializer())
        .registerTypeAdapter(ThrowableObject::class.java, ThrowableObjectSerializer())
        .apply {
            if (!useCustomDateSerializer) {
                registerTypeAdapter(Instant::class.java, InstantSerializer())
            }
        }
        .create()

    private val textFormatter = TextFormatter()

    override fun apply(record: LogRecord): SerializedLog.String = try {
        SerializedLog.String(record, gson.toJson(record))
    } catch (e: Exception) {
        val fallback = textFormatter.apply(record)

        SerializedLog.String(record, "ERROR: GsonFormatter failed: ${e.message}\n${fallback.serialized}")
    }

    private class InstantSerializer : JsonSerializer<Instant> {

        override fun serialize(src: Instant?, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            if (src == null) {
                return JsonNull.INSTANCE
            }

            val timestamp = src.toEpochMilliseconds()

            return JsonPrimitive(timestamp)
        }
    }

    private inner class ThrowableSerializer : JsonSerializer<Throwable> {

        override fun serialize(src: Throwable, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            if (traceLimit == Int.MAX_VALUE) {
                val writer = StringWriter()
                src.printStackTrace(PrintWriter(writer))
                return JsonPrimitive(writer.toString())
            }

            val builder = StringBuilder()

            builder.appendLine(src.toString())

            src.stackTrace
                .take(traceLimit)
                .forEach {
                    builder.appendLine("\tat $it")
                }

            return JsonPrimitive(builder.toString())
        }
    }

    private class ThrowableObjectSerializer : JsonSerializer<ThrowableObject> {

        override fun serialize(
            src: ThrowableObject,
            typeOfSrc: Type,
            context: JsonSerializationContext,
        ): JsonElement {
            val throwable = src.throwable ?: return JsonNull.INSTANCE

            val fields = throwable::class.memberProperties
                .filter { it.visibility == KVisibility.PUBLIC && !it.isSuspend && it.name != "cause" }
                .associate { it.name to it.call(throwable) }

            return context.serialize(fields)
        }
    }
}