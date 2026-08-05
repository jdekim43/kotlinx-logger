@file:Suppress("unused")

package kim.jade.kotlinx.logger

import co.touchlab.stately.collections.SharedHashMap
import kim.jade.kotlinx.extension.qualifiedOrSimpleName
import kim.jade.kotlinx.logger.context.CoroutineLogContext
import kim.jade.kotlinx.logger.context.LogContext
import kim.jade.kotlinx.logger.context.snapCurrentLogContext
import kim.jade.kotlinx.logger.pipeline.LogPipeline
import kim.jade.kotlinx.logger.pipeline.LoggerNameShortener
import kim.jade.kotlinx.logger.pipeline.StdOutSink
import kim.jade.kotlinx.logger.pipeline.TextFormatter
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

internal expect fun initPlatformLogger()

@OptIn(ExperimentalAtomicApi::class)
private var loggerInitialized = AtomicBoolean(false)

@OptIn(ExperimentalAtomicApi::class)
private fun initLogger() {
    if (loggerInitialized.compareAndSet(false, true)) {
        initPlatformLogger()
    }
}

open class Logger(val name: String, level: LogLevel? = null, pipeline: LogPipeline? = null) {

    companion object {

        var defaultLoggerName: String = "default"

        var level: LogLevel = LogLevel.INFO

        var pipeline: LogPipeline = LogPipeline()
            .install(LoggerNameShortener())
            .install(TextFormatter())
            .install(StdOutSink())

        private val loggers = SharedHashMap<String, Logger>()

        init {
            initLogger()
        }

        operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): Lazy<Logger> = if (thisRef == null) {
            lazy(defaultLoggerName)
        } else {
            lazy(thisRef::class)
        }

        fun lazy(name: String): Lazy<Logger> = lazy { named(name) }

        fun lazy(klass: KClass<*>): Lazy<Logger> = lazy { typed(klass) }

        inline fun <reified T> lazy(): Lazy<Logger> = lazy(T::class)

        fun named(name: String): Logger = loggers.getOrPut(name) { Logger(name) }

        fun typed(klass: KClass<*>): Logger = named(klass.qualifiedOrSimpleName ?: defaultLoggerName)

        inline fun <reified T> typed(): Logger = typed(T::class)
    }

    class LogProperties {
        var exception: Throwable? = null
        var eventName: String? = null
        var meta: LogMeta = emptyMap()
        var context: LogContext = snapCurrentLogContext()

        suspend fun withCoroutine() {
            context += CoroutineLogContext.get()
        }
    }

    private var _level: LogLevel? = level
    var level: LogLevel
        get() = _level ?: Logger.level
        set(value) {
            _level = value
        }

    private var _pipeline: LogPipeline? = pipeline
    var pipeline: LogPipeline
        get() = _pipeline ?: Logger.pipeline
        set(value) {
            _pipeline = value
        }

    fun log(record: LogRecord) {
        if (!record.isPrintableAt(level)) {
            return
        }

        pipeline.handle(record)
    }

    fun log(
        level: LogLevel,
        message: String,
        exception: Throwable? = null,
        eventName: String? = null,
        meta: LogMeta = emptyMap(),
        context: LogContext = snapCurrentLogContext(),
    ) {
        val record = LogRecordData(
            loggerName = name,
            level = level,
            body = message,
            exception = exception,
            eventName = eventName,
            meta = meta,
            context = context,
        )

        log(record)
    }

    inline fun log(level: LogLevel, body: LogProperties.() -> String) {
        if (!level.isPrintableAt(this.level)) {
            return
        }

        val properties = LogProperties()
        val message = properties.body()

        val record = LogRecordData(
            loggerName = name,
            level = level,
            body = message,
            exception = properties.exception,
            eventName = properties.eventName,
            meta = properties.meta,
            context = properties.context,
        )

        log(record)
    }

    fun fatal(
        message: String,
        exception: Throwable? = null,
        eventName: String? = null,
        meta: LogMeta = emptyMap(),
        context: LogContext = snapCurrentLogContext(),
    ) {
        log(LogLevel.FATAL, message, exception, eventName, meta, context)
    }

    inline fun fatal(body: LogProperties.() -> String) {
        log(LogLevel.FATAL, body)
    }

    fun error(
        message: String,
        exception: Throwable? = null,
        eventName: String? = null,
        meta: LogMeta = emptyMap(),
        context: LogContext = snapCurrentLogContext(),
    ) {
        log(LogLevel.ERROR, message, exception, eventName, meta, context)
    }

    inline fun error(body: LogProperties.() -> String) {
        log(LogLevel.ERROR, body)
    }

    fun warning(
        message: String,
        exception: Throwable? = null,
        eventName: String? = null,
        meta: LogMeta = emptyMap(),
        context: LogContext = snapCurrentLogContext(),
    ) {
        log(LogLevel.WARNING, message, exception, eventName, meta, context)
    }

    inline fun warning(body: LogProperties.() -> String) {
        log(LogLevel.WARNING, body)
    }

    fun info(
        message: String,
        exception: Throwable? = null,
        eventName: String? = null,
        meta: LogMeta = emptyMap(),
        context: LogContext = snapCurrentLogContext(),
    ) {
        log(LogLevel.INFO, message, exception, eventName, meta, context)
    }

    inline fun info(body: LogProperties.() -> String) {
        log(LogLevel.INFO, body)
    }

    fun debug(
        message: String,
        exception: Throwable? = null,
        eventName: String? = null,
        meta: LogMeta = emptyMap(),
        context: LogContext = snapCurrentLogContext(),
    ) {
        log(LogLevel.DEBUG, message, exception, eventName, meta, context)
    }

    inline fun debug(body: LogProperties.() -> String) {
        log(LogLevel.DEBUG, body)
    }

    fun trace(
        message: String,
        exception: Throwable? = null,
        eventName: String? = null,
        meta: LogMeta = emptyMap(),
        context: LogContext = snapCurrentLogContext(),
    ) {
        log(LogLevel.TRACE, message, exception, eventName, meta, context)
    }

    inline fun trace(body: LogProperties.() -> String) {
        log(LogLevel.TRACE, body)
    }
}