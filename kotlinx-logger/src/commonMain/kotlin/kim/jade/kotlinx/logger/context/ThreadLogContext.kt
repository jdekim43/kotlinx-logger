package kim.jade.kotlinx.logger.context

import kim.jade.kotlinx.thread.ThreadLocal

object ThreadLogContext : MutableLogContext {

    typealias MutableContextStack = MutableMap<String, MutableList<Any?>>

    internal val threadLocalData = ThreadLocal<MutableLogContext>()
    internal val threadLocalStack = ThreadLocal<MutableContextStack>()

    private val data: MutableLogContext
        get() {
            var data: MutableLogContext? = threadLocalData.get()

            if (data == null) {
                data = MutableLogContext()
                threadLocalData.set(data)
            }

            return data
        }

    private val stack: MutableContextStack
        get() {
            var stack: MutableContextStack? = threadLocalStack.get()

            if (stack == null) {
                stack = mutableMapOf()
                threadLocalStack.set(stack)
            }

            return stack
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

    fun push(key: String, value: Any?) {
        stack.getOrPut(key) { mutableListOf() }.add(value)
    }

    fun pop(key: String): Any? {
        val values = stack[key] ?: return null
        if (values.isEmpty()) {
            clearStack(key)
            return null
        }

        val value = values.removeLast()

        if (values.isEmpty()) {
            clearStack(key)
        }

        return value
    }

    fun copyAllInStack(): MutableContextStack = stack.deepCopy()

    fun copyAllInStack(key: String): MutableList<Any?>? = stack[key]?.toMutableList()

    fun clearStack(key: String) {
        stack.remove(key)
    }

    fun MutableContextStack.deepCopy(): MutableContextStack = mapValues { it.value.toMutableList() }.toMutableMap()
}
