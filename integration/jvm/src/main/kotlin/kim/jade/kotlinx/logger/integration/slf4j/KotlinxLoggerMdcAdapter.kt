package kim.jade.kotlinx.logger.integration.slf4j

import kim.jade.kotlinx.logger.context.ThreadLogContext
import org.slf4j.spi.MDCAdapter
import java.util.*

class KotlinxLoggerMdcAdapter : MDCAdapter {

    override fun clear() = ThreadLogContext.clear()

    override fun getCopyOfContextMap(): MutableMap<String, String?> = ThreadLogContext
        .filterValues { it == null || it is String }
        .mapValues { it.value as String? }
        .toMutableMap()

    override fun put(key: String, value: String?) {
        ThreadLogContext[key] = value
    }

    override fun setContextMap(contextMap: MutableMap<String, String?>?) {
        ThreadLogContext.clear()
        contextMap?.let { ThreadLogContext.putAll(it) }
    }

    override fun pushByKey(key: String, value: String?) {
        if (value == null) throw NullPointerException("Value cannot be null")

        ThreadLogContext.push(key, value)
    }

    override fun popByKey(key: String): String? = ThreadLogContext.pop(key)?.toString()

    override fun getCopyOfDequeByKey(key: String): Deque<String>? = ThreadLogContext.copyAllInStack(key)
        ?.asReversed()
        ?.map { it.toString() }
        ?.let { ArrayDeque(it) }

    override fun clearDequeByKey(key: String) {
        ThreadLogContext.clearStack(key)
    }

    override fun remove(key: String) {
        ThreadLogContext.remove(key)
    }

    override fun get(key: String): String? = ThreadLogContext[key] as? String
}
