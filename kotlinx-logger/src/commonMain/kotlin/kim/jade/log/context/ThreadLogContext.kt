package kim.jade.log.context

import kim.jade.kotlinx.thread.ThreadLocal

object ThreadLogContext : MutableLogContext {

    internal val threadLocal = ThreadLocal<MutableLogContext>()

    private val data: MutableLogContext
        get() {
            var data: MutableLogContext? = threadLocal.get()

            if (data == null) {
                data = MutableLogContext()
                threadLocal.set(data)
            }

            return data
        }

    override val entries: MutableSet<MutableMap.MutableEntry<String, Any?>>
        get() = data.entries

    override val keys: MutableSet<String>
        get() = data.keys

    override val size: Int
        get() = data.size

    override val values: MutableCollection<Any?>
        get() = data.values

    override fun containsKey(key: String): Boolean = data.containsKey(key)

    override fun containsValue(value: Any?): Boolean = data.containsValue(value)

    override fun get(key: String): Any? = data[key]

    override fun isEmpty(): Boolean = data.isEmpty()

    override fun clear() = data.clear()

    override fun put(key: String, value: Any?): Any? = data.put(key, value)

    override fun putAll(from: Map<out String, Any?>) = data.putAll(from)

    override fun remove(key: String): Any? = data.remove(key)
}