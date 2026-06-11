package kr.jadekim.logger.coroutine.context

import kotlinx.coroutines.currentCoroutineContext
import kr.jadekim.logger.context.MutableLogContext
import kr.jadekim.logger.context.ThreadLogContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.JvmStatic

class CoroutineLogContext(
    data: MutableLogContext = MutableLogContext(),
) : AbstractCoroutineContextElement(Key), MutableLogContext by data {

    constructor(data: Map<String, Any?>) : this(MutableLogContext(data.toMutableMap()))

    companion object Key : CoroutineContext.Key<CoroutineLogContext> {

        @JvmStatic
        suspend fun get(coroutineContext: CoroutineContext? = null): CoroutineLogContext =
            (coroutineContext ?: currentCoroutineContext())[CoroutineLogContext]
                ?: CoroutineLogContext(ThreadLogContext.clone())
    }
}