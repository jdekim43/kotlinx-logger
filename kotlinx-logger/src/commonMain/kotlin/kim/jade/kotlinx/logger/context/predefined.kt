@file:Suppress("unused")

package kim.jade.kotlinx.logger.context

fun snapCurrentLogContext(): LogContext = GlobalLogContext + ThreadLogContext

object EmptyLogContext : LogContext by LogContext(emptyMap())

object GlobalLogContext : MutableLogContext by MutableLogContext()
