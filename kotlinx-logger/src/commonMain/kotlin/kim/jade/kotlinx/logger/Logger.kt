@file:Suppress("unused")

package kim.jade.kotlinx.logger

import co.touchlab.stately.collections.SharedHashMap
import kim.jade.kotlinx.extension.qualifiedOrSimpleName
import kim.jade.kotlinx.io.eprintln
import kim.jade.kotlinx.logger.context.CoroutineLogContext
import kim.jade.kotlinx.logger.context.LogContext
import kim.jade.kotlinx.logger.context.snapCurrentLogContext
import kim.jade.kotlinx.logger.pipeline.LogPipeline
import kim.jade.kotlinx.logger.pipeline.LoggerNameShortener
import kim.jade.kotlinx.logger.pipeline.StdOutSink
import kim.jade.kotlinx.logger.pipeline.TextFormatter
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

internal expect fun initPlatformLogger()

@OptIn(ExperimentalAtomicApi::class)
private var loggerInitialized = AtomicBoolean(false)

@OptIn(ExperimentalAtomicApi::class)
private var loggerCacheFullReported = AtomicBoolean(false)

@OptIn(ExperimentalAtomicApi::class)
private fun initLogger() {
    if (loggerInitialized.compareAndSet(false, true)) {
        initPlatformLogger()
    }
}

open class Logger(
    val name: String,
    level: LogLevel? = null,
    pipeline: LogPipeline? = null,
    parent: Logger? = null,
) {

    companion object {

        /** Name of the logger every other logger ultimately inherits from. */
        const val ROOT_LOGGER_NAME: String = ""

        /** Separates the segments of a logger name. */
        const val NAME_SEPARATOR: Char = '.'

        private const val MAX_HIERARCHY_DEPTH: Int = 1000

        var defaultLoggerName: String = "default"

        var maxRegisteredLoggers: Int = 4096

        val registeredLoggerCount: Int
            get() = loggers.size

        var defaultLevel: LogLevel = LogLevel.INFO
            set(value) {
                field = value
                ConfigurationSnapshot.invalidate()
            }

        var defaultPipeline: LogPipeline = LogPipeline()
            .install(LoggerNameShortener())
            .install(TextFormatter())
            .install(StdOutSink())
            set(value) {
                field = value
                ConfigurationSnapshot.invalidate()
            }

        val root: Logger

        private val loggers = SharedHashMap<String, Logger>()

        init {
            root = named(ROOT_LOGGER_NAME)

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

        fun named(name: String): Logger = loggers[name] ?: if (loggers.size >= maxRegisteredLoggers) {
            reportLoggerCacheFull(name)

            Logger(name)
        } else {
            loggers.getOrPut(name) { Logger(name) }
        }

        fun unregister(name: String): Boolean {
            if (name == ROOT_LOGGER_NAME) {
                return false
            }

            val removed = loggers.remove(name) != null
            if (removed) {
                ConfigurationSnapshot.invalidate()
            }

            return removed
        }

        fun typed(klass: KClass<*>): Logger = named(klass.qualifiedOrSimpleName ?: defaultLoggerName)

        inline fun <reified T> typed(): Logger = typed(T::class)

        fun configure(name: String, block: Logger.() -> Unit): Logger = named(name).apply(block)

        fun resetAllConfiguration() {
            loggers.values.forEach { it.resetConfiguration() }

            ConfigurationSnapshot.invalidate()
        }

        private fun registered(name: String): Logger? = loggers[name]

        @OptIn(ExperimentalAtomicApi::class)
        private fun reportLoggerCacheFull(name: String) {
            if (loggerCacheFullReported.compareAndSet(false, true)) {
                eprintln(
                    "WARN: Logger: the logger cache holds maxRegisteredLoggers=$maxRegisteredLoggers entries; " +
                            "'$name' and later names are not cached. Logger names should come from a bounded " +
                            "set — pass request data as metadata or context instead."
                )
            }
        }
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

    internal class ConfigurationSnapshot(
        val generation: Int,
        val parent: Logger?,
        val level: LogLevel,
        val pipeline: LogPipeline,
    ) {

        @OptIn(ExperimentalAtomicApi::class)
        companion object {

            private val generation = AtomicInt(0)

            fun current(): Int = generation.load()

            fun invalidate() {
                generation.incrementAndFetch()
            }
        }
    }

    private var configuredParent: Logger? = parent
    private var configuredLevel: LogLevel? = level
    private var configuredPipeline: LogPipeline? = pipeline
    private var configurePipelineFromParent: (LogPipeline.() -> Unit)? = null

    private var resolved: ConfigurationSnapshot? = null
    private val configuration: ConfigurationSnapshot
        get() {
            val generation = ConfigurationSnapshot.current()
            val cached = resolved
            if (cached != null && cached.generation == generation) {
                return cached
            }

            val parent = configuredParent ?: resolveParent()

            return ConfigurationSnapshot(
                generation = generation,
                parent = parent,
                level = configuredLevel ?: inheritedLevel(parent),
                pipeline = configuredPipeline ?: inheritedPipeline(parent),
            ).also { resolved = it }
        }

    var parent: Logger?
        get() = configuration.parent
        set(value) {
            var ancestor: Logger? = value
            var depth = 0
            while (ancestor != null) {
                require(ancestor !== this) { "'$name' cannot be a descendant of itself" }
                require(depth++ < MAX_HIERARCHY_DEPTH) {
                    "'$name' cannot inherit from '${value?.name}': the chain above it is longer than " +
                            "$MAX_HIERARCHY_DEPTH, so it cannot be checked for a cycle"
                }

                ancestor = ancestor.run { configuredParent ?: resolveParent() }
            }

            configuredParent = value
            ConfigurationSnapshot.invalidate()
        }

    var level: LogLevel
        get() = configuration.level
        set(value) {
            configuredLevel = value
            ConfigurationSnapshot.invalidate()
        }

    var pipeline: LogPipeline
        get() = configuration.pipeline
        set(value) {
            configuredPipeline = value
            ConfigurationSnapshot.invalidate()
        }

    fun configurePipelineFromParent(configure: LogPipeline.() -> Unit) {
        configurePipelineFromParent = configure
        ConfigurationSnapshot.invalidate()
    }

    fun useParentLevel() {
        configuredLevel = null
        ConfigurationSnapshot.invalidate()
    }

    fun useParentPipeline() {
        configuredPipeline = null
        configurePipelineFromParent = null
        ConfigurationSnapshot.invalidate()
    }

    fun resetParent() {
        configuredParent = null
        ConfigurationSnapshot.invalidate()
    }

    fun resetConfiguration() {
        configuredParent = null
        configuredLevel = null
        configuredPipeline = null
        configurePipelineFromParent = null
        ConfigurationSnapshot.invalidate()
    }

    fun log(record: LogRecord) {
        val configuration = configuration

        if (!record.isPrintableAt(configuration.level)) {
            return
        }

        configuration.pipeline.handle(record)
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

    /**
     * Level of the nearest ancestor that configured one, or [defaultLevel].
     *
     * Walking the chain here instead of reading `parent.level` keeps resolution bounded. The [parent] setter
     * rejects a cycle, but two threads pointing two loggers at each other at the same time can both pass that
     * check, and a recursive resolution would then overflow the stack on every later log call.
     */
    private fun inheritedLevel(parent: Logger?): LogLevel {
        var ancestor = parent
        var depth = 0

        while (ancestor != null && depth++ < MAX_HIERARCHY_DEPTH) {
            ancestor.configuredLevel?.let { return it }
            ancestor = ancestor.run { configuredParent ?: resolveParent() }
        }

        return defaultLevel
    }

    /**
     * Pipeline of the nearest ancestor that configured one, with every `configurePipelineFromParent` between
     * that ancestor and this logger applied on the way down. Bounded for the same reason as [inheritedLevel].
     */
    private fun inheritedPipeline(parent: Logger?): LogPipeline {
        val descendants = mutableListOf<Logger>()
        var ancestor = parent
        var configured: LogPipeline? = null
        var depth = 0

        while (ancestor != null && depth++ < MAX_HIERARCHY_DEPTH) {
            val ancestorPipeline = ancestor.configuredPipeline
            if (ancestorPipeline != null) {
                configured = ancestorPipeline
                break
            }

            descendants.add(ancestor)
            ancestor = ancestor.run { configuredParent ?: resolveParent() }
        }

        var pipeline = configured ?: defaultPipeline
        for (index in descendants.indices.reversed()) {
            pipeline = descendants[index].run { pipeline.inherited() }
        }

        return pipeline.inherited()
    }

    private fun resolveParent(): Logger? {
        if (name == ROOT_LOGGER_NAME) {
            return null
        }

        var cut = name.length
        while (cut > 0) {
            cut = name.lastIndexOf(NAME_SEPARATOR, cut - 1)
            if (cut <= 0) {
                break
            }

            registered(name.substring(0, cut))?.let { return it }
        }

        return root
    }

    private fun LogPipeline.inherited(): LogPipeline {
        val configure = configurePipelineFromParent ?: return this

        return clone().also { it.silently(configure) }
    }
}
