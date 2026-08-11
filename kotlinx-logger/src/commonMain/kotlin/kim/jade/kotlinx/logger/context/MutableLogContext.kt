@file:Suppress("unused")

package kim.jade.kotlinx.logger.context

import co.touchlab.stately.collections.SharedHashMap

interface MutableLogContext : LogContext, MutableMap<String, Any?> {

    override operator fun plus(other: Map<String, Any?>?): MutableLogContext = if (other == null) clone() else {
        val data = MutableLogContextMap()

        data.putAll(this)
        data.putAll(other)

        data
    }

    operator fun plusAssign(other: Map<String, Any?>?) {
        if (other != null) {
            putAll(other)
        }
    }

    override fun clone(): MutableLogContext = MutableLogContextCopied(this)

    fun toImmutable(): LogContext = LogContextCopied(this)
}

internal open class MutableLogContextMap : MutableLogContext, MutableMap<String, Any?> by SharedHashMap()

internal class MutableLogContextCopied(
    data: Map<String, Any?>,
) : MutableLogContextMap() {

    init {
        putAll(data)
    }
}

internal class MutableLogContextPairs(
    data: Array<out Pair<String, Any?>>,
) : MutableLogContextMap() {

    init {
        putAll(data)
    }
}

fun MutableLogContext(data: Map<String, Any?> = emptyMap()): MutableLogContext = MutableLogContextCopied(data)

fun MutableLogContext(vararg data: Pair<String, Any?>): MutableLogContext = MutableLogContextPairs(data)
