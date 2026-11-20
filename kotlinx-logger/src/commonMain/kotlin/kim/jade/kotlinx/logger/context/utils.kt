package kim.jade.kotlinx.logger.context

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

fun snapCurrentLogContext(): LogContext = GlobalLogContext + ThreadLogContext

fun CoroutineContext.logContext(): CoroutineLogContext = this[CoroutineLogContext] ?: CoroutineLogContext()

suspend fun currentCoroutineLogContext(): CoroutineLogContext = currentCoroutineContext().logContext()

fun LogContext.asCoroutineLogContext(parentContext: CoroutineContext): CoroutineContext {
    val logContext = this as? CoroutineLogContext ?: CoroutineLogContext(this)
    return logContext + parentContext.threadLogContextForScope()
}

suspend fun LogContext.asCoroutineLogContext(): CoroutineContext = asCoroutineLogContext(currentCoroutineContext())

suspend fun <R> withLogContext(
    logContext: CoroutineLogContext,
    merge: Boolean = true,
    block: suspend CoroutineScope.() -> R,
): R = withLogContext(data = logContext, merge = merge, block = block)

suspend fun <R> withLogContext(
    data: Map<String, Any?>,
    merge: Boolean = true,
    block: suspend CoroutineScope.() -> R,
): R {
    val parentContext = currentCoroutineContext()
    val parentLogContext = if (merge) parentContext[CoroutineLogContext] else null
    val logContext = if (parentLogContext != null) {
        CoroutineLogContext(parentLogContext + data)
    } else {
        data as? CoroutineLogContext ?: CoroutineLogContext(data)
    }
    return withContext(logContext.asCoroutineLogContext(parentContext), block)
}

suspend fun <R> withLogContext(
    vararg data: Pair<String, Any?>,
    merge: Boolean = true,
    block: suspend CoroutineScope.() -> R,
): R = withLogContext(data.toMap(), merge, block)

internal expect fun CoroutineContext.threadLogContextForScope(): CoroutineContext
