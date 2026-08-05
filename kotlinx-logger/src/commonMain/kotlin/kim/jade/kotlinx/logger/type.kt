@file:Suppress("unused")

package kim.jade.kotlinx.logger

import kim.jade.kotlinx.thread.currentThreadName
import kim.jade.kotlinx.logger.context.LogContext
import kim.jade.kotlinx.logger.context.snapCurrentLogContext
import kotlin.time.Clock
import kotlin.time.Instant

typealias LogMeta = Map<String, Any?>

interface LogRecord {
    val loggerName: String
    val level: LogLevel
    val body: String
    val exception: Throwable?
    val eventName: String?
    val meta: LogMeta
    val context: LogContext
    val threadName: String?
    val timestamp: Instant

    fun isPrintableAt(level: LogLevel) = this.level.isPrintableAt(level)
}

data class LogRecordData(
    override val loggerName: String,
    override val level: LogLevel,
    override val body: String,
    override val exception: Throwable? = null,
    override val eventName: String? = null,
    override val meta: LogMeta = emptyMap(),
    override val context: LogContext = snapCurrentLogContext(),
    override val threadName: String? = currentThreadName(),
    override val timestamp: Instant = Clock.System.now(),
) : LogRecord

abstract class SerializedLog<T>(record: LogRecord, val serialized: T) : LogRecord by record {

    class String(record: LogRecord, data: kotlin.String) : SerializedLog<kotlin.String>(record, data)

    class ByteArray(record: LogRecord, data: kotlin.ByteArray) : SerializedLog<kotlin.ByteArray>(record, data)
}

class ThrowableObject(val throwable: Throwable?)

fun Throwable?.asLogObject() = ThrowableObject(this)
