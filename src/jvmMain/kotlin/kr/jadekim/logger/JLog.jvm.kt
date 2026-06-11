package kr.jadekim.logger

import kr.jadekim.logger.integration.JulLogger
import kotlin.reflect.KClass

internal actual fun init() {
    JulLogger.removeHandlersForRootLogger()
    JulLogger.install()
}

fun JLog.get(klass: KClass<*>) = get(klass.qualifiedName ?: klass.java.canonicalName)

fun JLog.get(clazz: Class<*>) = get(clazz.canonicalName)