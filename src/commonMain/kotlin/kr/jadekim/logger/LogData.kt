package kr.jadekim.logger

import kr.jadekim.logger.context.EmptyLogContext
import kr.jadekim.logger.context.LogContext
import kotlin.time.Clock
import kotlin.time.Instant

interface Log {
    val loggerName: String
    val level: LogLevel
    val message: String
    val throwable: Throwable?
    val meta: Map<String, Any?>
    val context: LogContext
    val threadName: String?
    val timestamp: Instant

    fun isPrintable(level: LogLevel) = this.level.isPrintableAt(level)
}

data class LogData(
    override val loggerName: String,
    override val level: LogLevel,
    override val message: String,
    override val throwable: Throwable? = null,
    override val meta: Map<String, Any?> = emptyMap(),
    override val context: LogContext = EmptyLogContext,
    override val threadName: String? = getThreadName(),
    override val timestamp: Instant = Clock.System.now(),
) : Log

abstract class SerializedLog<T>(log: Log, val data: T) : Log by log {

    class LogString(log: Log, data: String) : SerializedLog<String>(log, data)

    class LogByteArray(log: Log, data: ByteArray) : SerializedLog<ByteArray>(log, data)
}

internal expect fun getThreadName(): String?

class ThrowableObjectLog(val throwable: Throwable?)

fun Throwable?.objectLog() = ThrowableObjectLog(this)
