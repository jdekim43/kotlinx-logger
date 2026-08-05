@file:Suppress("unused")

package kim.jade.log.context

interface LogContext : Map<String, Any?> {

    operator fun plus(other: Map<String, Any?>?): LogContext = if (other == null) this else {
        LogContext((this as Map<String, Any?>).plus(other))
    }

    fun clone(): LogContext = LogContext(toMap())

    fun toImmutable(): LogContext = LogContext(toMap())

    fun toMutable(): MutableLogContext = MutableLogContext(toMutableMap())
}

private class LogContextImpl(data: Map<String, Any?>) : LogContext, Map<String, Any?> by data.toMap()

fun LogContext(data: Map<String, Any?> = emptyMap()): LogContext = LogContextImpl(data)
