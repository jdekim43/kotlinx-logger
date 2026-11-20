package kim.jade.kotlinx.logger.context

import kotlin.coroutines.CoroutineContext

internal actual fun CoroutineContext.threadLogContextForScope(): CoroutineContext =
    this[CoroutineThreadLogContext] ?: CoroutineThreadLogContext()
