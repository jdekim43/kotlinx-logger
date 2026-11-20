@file:Suppress("unused")

package kim.jade.kotlinx.logger.context

import kotlinx.coroutines.CopyableThreadContextElement
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
class CoroutineThreadLogContext private constructor(
    val data: ThreadLogContext.Data,
    private val hiddenCoroutineKeys: MutableSet<String>?,
) : AbstractCoroutineContextElement(Key),
    CopyableThreadContextElement<ThreadLogContext.Data> {

    companion object Key : CoroutineContext.Key<CoroutineThreadLogContext>

    private val threadFields = data.data ?: MutableLogContext()
    private val threadData = ThreadLogContext.Data(threadFields, data.stack ?: mutableMapOf())

    @JvmOverloads
    constructor(data: ThreadLogContext.Data = captureThreadData()) : this(includeCoroutineContext = true, data = data)

    @JvmOverloads
    constructor(includeCoroutineContext: Boolean, data: ThreadLogContext.Data = captureThreadData()) : this(
        data,
        if (includeCoroutineContext) newHiddenCoroutineKeys() else null,
    )

    constructor(context: MutableLogContext?, stack: ThreadLogContext.MutableContextStack?) : this(
        ThreadLogContext.Data(context, stack)
    )

    constructor(context: MutableLogContext?) : this(context, null)

    constructor(stack: ThreadLogContext.MutableContextStack?) : this(null, stack)

    override fun updateThreadContext(context: CoroutineContext): ThreadLogContext.Data {
        val oldState = ThreadLogContext.captureState()
        val coroutineFields = context[CoroutineLogContext]
        val hiddenKeys = hiddenCoroutineKeys
        val installedData = if (coroutineFields != null && hiddenKeys != null) {
            threadData.copy(data = CoroutineLogContextMap(threadFields, coroutineFields, hiddenKeys))
        } else {
            threadData
        }
        ThreadLogContext.restoreData(installedData)
        return oldState
    }

    override fun restoreThreadContext(
        context: CoroutineContext,
        oldState: ThreadLogContext.Data,
    ) {
        ThreadLogContext.restoreData(oldState)
    }

    override fun copyForChild(): CoroutineThreadLogContext = CoroutineThreadLogContext(
        threadData.clone(),
        hiddenCoroutineKeys?.let { newHiddenCoroutineKeys().apply { addAll(it) } },
    )

    override fun mergeForChild(overwritingElement: CoroutineContext.Element): CoroutineContext =
        (overwritingElement as? CoroutineThreadLogContext)?.copyForChild() ?: overwritingElement
}

private fun newHiddenCoroutineKeys(): MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

private fun captureThreadData(): ThreadLogContext.Data {
    val current = ThreadLogContext.captureState()
    val threadFields = (current.data as? CoroutineLogContextMap)?.threadFields ?: current.data
    return current.copy(data = threadFields).clone()
}
