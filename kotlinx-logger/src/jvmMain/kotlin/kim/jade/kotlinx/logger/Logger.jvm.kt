@file:Suppress("unused")

package kim.jade.kotlinx.logger

import java.util.ServiceLoader

internal actual fun initPlatformLogger() {
    ServiceLoader.load(LoggerInitializerProvider::class.java).forEach { initializer ->
        initializer.run()
    }
}

fun Logger.Companion.typed(clazz: Class<*>): Logger = named(clazz.canonicalName ?: Logger.defaultLoggerName)
