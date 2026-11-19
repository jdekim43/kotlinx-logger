@file:Suppress("unused")

package kim.jade.kotlinx.logger.context

object EmptyLogContext : LogContext by LogContext(emptyMap())

object GlobalLogContext : MutableLogContext by MutableLogContext()
