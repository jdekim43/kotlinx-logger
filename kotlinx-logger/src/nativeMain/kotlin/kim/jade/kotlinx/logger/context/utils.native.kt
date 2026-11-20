package kim.jade.kotlinx.logger.context

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

internal actual fun CoroutineContext.threadLogContextForScope(): CoroutineContext = EmptyCoroutineContext
