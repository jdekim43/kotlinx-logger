package kim.jade.log.integration.slf4j

import kim.jade.log.context.ThreadLogContext
import org.slf4j.spi.MDCAdapter
import java.util.*

class KotlinxLoggerMdcAdapter : MDCAdapter {

    override fun clear() = ThreadLogContext.clear()

    override fun getCopyOfContextMap(): MutableMap<String, String> = ThreadLogContext
        .filterValues { it is String }
        .mapValues { it.value.toString() }
        .toMutableMap()

    override fun put(key: String, value: String?) {
        ThreadLogContext[key] = value
    }

    override fun setContextMap(contextMap: MutableMap<String, String?>) {
        ThreadLogContext.putAll(contextMap.mapValues { it.value }.toMutableMap())
    }

    override fun pushByKey(key: String, value: String?) {
        ThreadLogContext[key] = value
    }

    override fun popByKey(key: String): String {
        return ThreadLogContext[key].toString()
    }

    override fun getCopyOfDequeByKey(key: String): Deque<String> {
        val deque = ArrayDeque<String>()

        ThreadLogContext.clone().entries.forEach {
            deque.push(it.value.toString())
        }

        return deque
    }

    override fun clearDequeByKey(key: String) {
        ThreadLogContext.remove(key)
    }

    override fun remove(key: String) {
        ThreadLogContext.remove(key)
    }

    override fun get(key: String): String? = ThreadLogContext[key]?.toString()
}