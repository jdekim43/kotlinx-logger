package kim.jade.kotlinx.logger.context

internal class CoroutineLogContextMap(
    val threadFields: MutableLogContext,
    private val coroutineFields: CoroutineLogContext,
    private val hiddenKeys: MutableSet<String>,
) : MutableLogContext {

    override val entries: MutableSet<MutableMap.MutableEntry<String, Any?>> get() = snapshot().entries
    override val keys: MutableSet<String> get() = snapshot().keys
    override val values: MutableCollection<Any?> get() = snapshot().values
    override val size: Int get() = snapshot().size

    override fun clone(): MutableLogContext = snapshot()

    override fun isEmpty(): Boolean = size == 0

    override fun containsValue(value: Any?): Boolean = snapshot().containsValue(value)

    override fun containsKey(key: String): Boolean =
        threadFields.hasField(key) || (key !in hiddenKeys && coroutineFields.hasField(key))

    override fun get(key: String): Any? {
        val value = threadFields[key]
        return when {
            value != null || key in threadFields.keys -> value
            key in hiddenKeys -> null
            else -> coroutineFields[key]
        }
    }

    override fun put(key: String, value: Any?): Any? {
        val previous = get(key)
        threadFields[key] = value
        hiddenKeys.remove(key)
        return previous
    }

    override fun putAll(from: Map<out String, Any?>) {
        threadFields.putAll(from)
        hiddenKeys.removeAll(from.keys)
    }

    override fun remove(key: String): Any? {
        val previous = get(key)
        threadFields.remove(key)
        if (coroutineFields.hasField(key)) hiddenKeys.add(key)
        return previous
    }

    override fun clear() {
        threadFields.clear()
        hiddenKeys.addAll(coroutineFields.keys)
    }

    private fun snapshot(): MutableLogContext {
        val fields = MutableLogContext()
        for ((key, value) in coroutineFields) {
            if (key !in hiddenKeys) fields[key] = value
        }
        fields.putAll(threadFields)
        return fields
    }
}

private fun Map<String, Any?>.hasField(key: String): Boolean = containsKey(key) || key in keys
