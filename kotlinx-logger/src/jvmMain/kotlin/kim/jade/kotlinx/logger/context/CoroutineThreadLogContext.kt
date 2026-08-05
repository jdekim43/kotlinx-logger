package kim.jade.kotlinx.logger.context

import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class CoroutineThreadLogContext(
    val context: MutableLogContext = ThreadLogContext.toMutable()
) : AbstractCoroutineContextElement(Key), ThreadContextElement<MutableLogContext> {

    companion object Key : CoroutineContext.Key<CoroutineThreadLogContext>

    override fun updateThreadContext(context: CoroutineContext): MutableLogContext {
        val oldState = ThreadLogContext.toMutable()
        ThreadLogContext.threadLocal.set(this.context)
        return oldState
    }

    override fun restoreThreadContext(
        context: CoroutineContext,
        oldState: MutableLogContext
    ) {
        ThreadLogContext.threadLocal.set(oldState)
    }
}