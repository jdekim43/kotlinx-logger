@file:Suppress("unused")

package kim.jade.log.context

import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.JvmStatic

class CoroutineLogContext(
    context: MutableLogContext = MutableLogContext(),
) : AbstractCoroutineContextElement(Key), MutableLogContext by context {

    constructor(data: Map<String, Any?>) : this(MutableLogContext(data.toMutableMap()))

    companion object Key : CoroutineContext.Key<CoroutineLogContext> {

        @JvmStatic
        suspend fun get(coroutineContext: CoroutineContext? = null): CoroutineLogContext =
            (coroutineContext ?: currentCoroutineContext())[CoroutineLogContext] ?: CoroutineLogContext()
    }
}