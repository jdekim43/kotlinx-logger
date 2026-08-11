@file:Suppress("unused")

package kim.jade.kotlinx.logger.context

interface LogContext : Map<String, Any?> {

    operator fun plus(other: Map<String, Any?>?): LogContext = if (other == null) this else {
        LogContextMap((this as Map<String, Any?>).plus(other))
    }

    fun clone(): LogContext = LogContextCopied(this)

    fun toMutable(): MutableLogContext = MutableLogContextCopied(this)
}

internal open class LogContextMap(data: Map<String, Any?>) : LogContext, Map<String, Any?> by data

internal class LogContextCopied(data: Map<String, Any?>) : LogContextMap(data.toMap())

internal class LogContextPairs(data: Array<out Pair<String, Any?>>) : LogContextMap(data.toMap())

fun LogContext(data: Map<String, Any?> = emptyMap()): LogContext = LogContextCopied(data)

fun LogContext(vararg data: Pair<String, Any?>): LogContext = LogContextPairs(data)
