package kim.jade.kotlinx.logger.context

import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.CoroutineContext

fun snapCurrentLogContext(): LogContext = GlobalLogContext + ThreadLogContext

fun CoroutineContext.logContext(): CoroutineLogContext = this[CoroutineLogContext] ?: CoroutineLogContext()

suspend fun currentCoroutineLogContext(): CoroutineLogContext = currentCoroutineContext().logContext()
