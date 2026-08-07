package kim.jade.kotlinx.logger.context

import kim.jade.kotlinx.logger.context.ThreadLogContext.deepCopy
import kotlinx.coroutines.CopyableThreadContextElement
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
class CoroutineThreadLogContext(
    val context: MutableLogContext = ThreadLogContext.toMutable(),
    val contextStack: ThreadLogContext.MutableContextStack = ThreadLogContext.copyAllInStack(),
) : AbstractCoroutineContextElement(Key),
    CopyableThreadContextElement<CoroutineThreadLogContext.ThreadLogContextState> {

    companion object Key : CoroutineContext.Key<CoroutineThreadLogContext>

    data class ThreadLogContextState(
        val data: MutableLogContext?,
        val stack: ThreadLogContext.MutableContextStack?,
    )

    override fun updateThreadContext(context: CoroutineContext): ThreadLogContextState {
        val oldState = ThreadLogContextState(
            data = ThreadLogContext.toMutable(),
            stack = ThreadLogContext.copyAllInStack(),
        )

        ThreadLogContext.threadLocalData.set(this.context)
        ThreadLogContext.threadLocalStack.set(this.contextStack)

        return oldState
    }

    override fun restoreThreadContext(
        context: CoroutineContext,
        oldState: ThreadLogContextState,
    ) {
        ThreadLogContext.threadLocalData.set(oldState.data)
        ThreadLogContext.threadLocalStack.set(oldState.stack)
    }

    override fun copyForChild(): CoroutineThreadLogContext =
        CoroutineThreadLogContext(context.toMutable(), contextStack.deepCopy())

    override fun mergeForChild(overwritingElement: CoroutineContext.Element): CoroutineContext =
        (overwritingElement as? CoroutineThreadLogContext)?.copyForChild() ?: overwritingElement
}
