@file:Suppress("unused")

package kim.jade.kotlinx.logger.context

import kotlinx.coroutines.CopyableThreadContextElement
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
class CoroutineThreadLogContext(
    val data: ThreadLogContext.Data = ThreadLogContext.captureData(),
) : AbstractCoroutineContextElement(Key),
    CopyableThreadContextElement<ThreadLogContext.Data> {

    companion object Key : CoroutineContext.Key<CoroutineThreadLogContext>

    constructor(context: MutableLogContext?, stack: ThreadLogContext.MutableContextStack?) : this(
        ThreadLogContext.Data(context, stack)
    )

    constructor(context: MutableLogContext?) : this(context, null)

    constructor(stack: ThreadLogContext.MutableContextStack?) : this(null, stack)

    override fun updateThreadContext(context: CoroutineContext): ThreadLogContext.Data {
        val oldState = ThreadLogContext.captureData()

        ThreadLogContext.restoreData(data)

        return oldState
    }

    override fun restoreThreadContext(
        context: CoroutineContext,
        oldState: ThreadLogContext.Data,
    ) {
        ThreadLogContext.restoreData(oldState)
    }

    override fun copyForChild(): CoroutineThreadLogContext = CoroutineThreadLogContext(data.clone())

    override fun mergeForChild(overwritingElement: CoroutineContext.Element): CoroutineContext =
        (overwritingElement as? CoroutineThreadLogContext)?.copyForChild() ?: overwritingElement
}
