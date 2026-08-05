@file:Suppress("unused")

package kim.jade.log.context

import co.touchlab.stately.collections.SharedHashMap

interface MutableLogContext : LogContext, MutableMap<String, Any?> {

    override operator fun plus(other: Map<String, Any?>?): MutableLogContext = if (other == null) clone() else {
        val data = mutableMapOf<String, Any?>()

        data.putAll(this)
        data.putAll(other)

        MutableLogContext(data)
    }

    operator fun plusAssign(other: LogContext?) {
        if (other != null) {
            putAll(other)
        }
    }

    fun snap() = LogContext(toMap())

    override fun clone(): MutableLogContext = MutableLogContext(toMutableMap())
}

internal class MutableLogContextImpl(
    data: Map<String, Any?>,
) : MutableLogContext,
    MutableMap<String, Any?> by SharedHashMap() {

    init {
        putAll(data)
    }
}

fun MutableLogContext(data: Map<String, Any?> = emptyMap()): MutableLogContext = MutableLogContextImpl(data)
