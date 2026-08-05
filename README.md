# kotlinx-logger

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-blue.svg)](https://kotlinlang.org/)
[![Maven Central](https://img.shields.io/maven-central/v/kim.jade/kotlinx-logger.svg)](https://central.sonatype.com/artifact/kim.jade/kotlinx-logger)

`kotlinx-logger` is a Kotlin Multiplatform logging library. It supports global, thread-local, and coroutine contexts, lazy message evaluation, and customizable pipelines. The JVM integration module also provides interoperability with JUL and SLF4J.

## Features

- A consistent logging API across Kotlin Multiplatform targets
- Global (`GlobalLogContext`), thread-local (`ThreadLogContext`), and coroutine (`CoroutineLogContext`) contexts
- A lazy logging DSL that avoids constructing messages before level filtering
- A composable `LogPipeline` for filtering, transforming, formatting, and emitting records
- Text formatting plus Gson and Jackson JSON formatters for the JVM
- Integrations for Koin, Ktor, OkHttp, OpenTelemetry, and Sentry
- A JUL handler and an SLF4J 2 provider

## Modules and supported platforms

| Module                  | Gradle artifact                            | Platform      |
|-------------------------|--------------------------------------------|---------------|
| Core                    | `kotlinx-logger`                           | Multiplatform |
| Gson                    | `kotlinx-logger-integration-gson`          | JVM           |
| Jackson 3               | `kotlinx-logger-integration-jackson`       | JVM           |
| JVM logging (JUL/SLF4J) | `kotlinx-logger-integration-jvm`           | JVM           |
| Koin                    | `kotlinx-logger-integration-koin`          | Multiplatform |
| Ktor                    | `kotlinx-logger-integration-ktor`          | Multiplatform |
| OkHttp                  | `kotlinx-logger-integration-okhttp`        | JVM           |
| OpenTelemetry           | `kotlinx-logger-integration-opentelemetry` | JVM           |
| Sentry                  | `kotlinx-logger-integration-sentry`        | JVM           |

The Multiplatform modules target JVM 11, Android, JS (browser and Node.js, ES2015), macOS Arm64, iOS Arm64/x64/Simulator Arm64, watchOS Arm64/Simulator Arm64, tvOS Arm64/Simulator Arm64, Linux x64/Arm64, and Windows MinGW x64.

## Installation

Add Maven Central, set the version shown in the badge above or on Maven Central, and include only the modules you need.

```kotlin
// build.gradle.kts
repositories {
    mavenCentral()
}

val kotlinxLoggerVersion = "<version>"

dependencies {
    implementation("kim.jade:kotlinx-logger:$kotlinxLoggerVersion")

    // Add only the integration modules you need.
    implementation("kim.jade:kotlinx-logger-integration-gson:$kotlinxLoggerVersion")
    implementation("kim.jade:kotlinx-logger-integration-jackson:$kotlinxLoggerVersion")
    implementation("kim.jade:kotlinx-logger-integration-jvm:$kotlinxLoggerVersion")
    implementation("kim.jade:kotlinx-logger-integration-koin:$kotlinxLoggerVersion")
    implementation("kim.jade:kotlinx-logger-integration-ktor:$kotlinxLoggerVersion")
    implementation("kim.jade:kotlinx-logger-integration-okhttp:$kotlinxLoggerVersion")
    implementation("kim.jade:kotlinx-logger-integration-opentelemetry:$kotlinxLoggerVersion")
    implementation("kim.jade:kotlinx-logger-integration-sentry:$kotlinxLoggerVersion")
}
```

In a Kotlin Multiplatform project, add the core module or Multiplatform integration modules to `commonMain`.

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("kim.jade:kotlinx-logger:$kotlinxLoggerVersion")
        }
    }
}
```

## Quick start

```kotlin
import kim.jade.kotlinx.logger.Logger

class OrderService {
    private val logger by Logger

    fun create(orderId: String) {
        logger.info {
            meta = mapOf("orderId" to orderId)
            "order created"
        }
    }
}
```

When `private val logger by Logger` is declared as a class member, the logger name is the class's qualified name. The default global level is `INFO`, and the default pipeline runs in this order:

```text
LoggerNameShortener -> TextFormatter -> StdOutSink
```

The default output has the following form. The timestamp and logger name vary at runtime.

```text
2026-08-05T08:00:00Z [main] INFO  com.example.OrderService            - order created (orderId=A-100)
```

## Creating a logger

### Immediate creation

```kotlin
val byName = Logger.named("payment")
val byClass = Logger.typed(PaymentService::class)
val byType = Logger.typed<PaymentService>()

// JVM only
val byJavaClass = Logger.typed(PaymentService::class.java)
```

`named(name)` caches instances by name, so calls with the same name return the same `Logger`. The Kotlin `typed` variants use the qualified name and then the simple name, while the JVM `Class` overload uses the canonical name. They fall back to `Logger.defaultLoggerName` when no name is available.

Call the constructor directly when you need an independent, uncached logger.

```kotlin
val auditLogger = Logger(
    name = "audit",
    level = LogLevel.INFO,
    pipeline = auditPipeline,
)
```

### Lazy creation and delegates

```kotlin
val namedLogger by Logger.lazy("payment")
val classLogger by Logger.lazy(PaymentService::class)
val typedLogger by Logger.lazy<PaymentService>()

class PaymentService {
    private val logger by Logger // PaymentService's qualified name
}

private val topLevelLogger by Logger // Logger.defaultLoggerName
```

| Function                            | Purpose                                                                 |
|-------------------------------------|-------------------------------------------------------------------------|
| `Logger.named(name)`                | Returns a cached logger for the given name.                             |
| `Logger.typed(KClass)`              | Returns a logger named after a Kotlin class.                            |
| `Logger.typed<T>()`                 | Returns a logger named after a reified type.                            |
| `Logger.typed(Class)`               | Returns a logger named after a Java class. JVM only.                    |
| `Logger.lazy(name)`                 | Creates a `Lazy<Logger>` that calls `named(name)` on first access.      |
| `Logger.lazy(KClass)` / `lazy<T>()` | Creates a type-based logger on first access.                            |
| `by Logger`                         | Uses the owning class name for members and the default name at top level. |

## Logging

### String overloads

Every level function accepts a message and optional properties directly.

```kotlin
logger.trace("trace message")
logger.debug("cache miss", meta = mapOf("key" to cacheKey))
logger.info("order created")
logger.warning("retrying request", exception = cause)
logger.error(
    message = "payment failed",
    exception = cause,
    eventName = "payment.failed",
    meta = mapOf("paymentId" to paymentId),
    context = requestContext,
)
logger.fatal("service cannot continue", exception = cause)
```

`trace`, `debug`, `info`, `warning`, `error`, and `fatal` all accept the same parameters.

| Parameter   | Default                   | Description                                             |
|-------------|---------------------------|---------------------------------------------------------|
| `message`   | Required                  | The log message.                                        |
| `exception` | `null`                    | An optional associated exception.                       |
| `eventName` | `null`                    | A structured event name.                                |
| `meta`      | `emptyMap()`              | Structured data attached only to this log record.       |
| `context`   | `snapCurrentLogContext()` | A call-time snapshot of the global and current thread contexts. |

An explicit `context` **replaces** the automatically captured context; it is not added to it.

### Lazy DSL overloads

```kotlin
logger.debug {
    exception = cause
    eventName = "query.failed"
    meta = mapOf("query" to expensiveQueryDescription())
    context = requestContext
    "query failed"
}
```

The DSL body is not evaluated when its level is disabled. Prefer this form when constructing the message or metadata is expensive. Inside the body, you can set `exception`, `eventName`, `meta`, and `context`; the final expression must be the message `String`.

### Generic `log` functions

```kotlin
logger.log(
    level = LogLevel.INFO,
    message = "manual log",
    meta = mapOf("source" to "batch"),
)

logger.log(LogLevel.DEBUG) {
    meta = mapOf("jobId" to jobId)
    "job started"
}

val record = LogRecordData(
    loggerName = "importer",
    level = LogLevel.WARNING,
    body = "invalid row",
    eventName = "import.invalid-row",
    meta = mapOf("row" to 42),
)
logger.log(record)
```

| Function                                                   | Behavior                                                                    |
|------------------------------------------------------------|-----------------------------------------------------------------------------|
| `log(record)`                                              | Checks the record's level and sends it through the logger's pipeline.       |
| `log(level, message, exception, eventName, meta, context)` | Creates a `LogRecordData` and passes it to `log(record)`.                   |
| `log(level) { ... }`                                       | Evaluates the DSL body and creates a record only when the level is enabled. |
| `trace/debug/info/warning/error/fatal(...)`                | Convenience functions for each level, with both string and DSL overloads.   |

## Log contexts

Context data is stored in `LogRecord.context` as a `Map<String, Any?>`. The default `TextFormatter` displays `meta` but not `context`. Context remains available to JSON formatters, Sentry, and custom pipes.

### Immutable and mutable contexts

```kotlin
val immutable = LogContext(
    mapOf("tenantId" to "tenant-a")
)

val mutable = MutableLogContext()
mutable["requestId"] = "req-100"
mutable.putAll(mapOf("userId" to 7, "role" to "admin"))
```

When keys overlap, the value on the right-hand side of `+` wins.

```kotlin
val base = LogContext(mapOf("tenant" to "a", "region" to "kr"))
val override = LogContext(mapOf("region" to "us"))
val merged = base + override // region == "us"

val mutableMerged = MutableLogContext(mapOf("a" to 1)) + mapOf("b" to 2)
mutableMerged += LogContext(mapOf("c" to 3))
```

| Function                         | Result                                                           |
|----------------------------------|------------------------------------------------------------------|
| `LogContext(data)`               | Copies the input into an immutable context.                      |
| `MutableLogContext(data)`        | Creates a shareable mutable context.                             |
| `context + map`                  | Merges values into a new context; values on the right win.       |
| `mutableContext += logContext`   | Adds values directly to the mutable context on the left.         |
| `clone()`                        | Creates a new context with the same data and mutability.         |
| `toImmutable()`                  | Creates an immutable copy.                                      |
| `toMutable()`                    | Creates a mutable copy.                                         |
| `MutableLogContext.snap()`       | Takes an immutable `LogContext` snapshot of the current values.  |

Other read and write operations behave like their Kotlin `Map` and `MutableMap` counterparts.

### Global and thread-local contexts

```kotlin
GlobalLogContext["service"] = "checkout"

try {
    ThreadLogContext["requestId"] = requestId
    logger.info("request received")
} finally {
    ThreadLogContext.clear()
}
```

`snapCurrentLogContext()` returns an immutable snapshot of `GlobalLogContext + ThreadLogContext`. Thread-local values win when keys overlap.

```kotlin
val snapshot = snapCurrentLogContext()

// Intentionally exclude the automatic global and thread-local contexts.
logger.info("health check", context = EmptyLogContext)
```

Because `ThreadLogContext` is thread-local, clear it at the appropriate time when using thread pools, or use the coroutine propagation utility.

### Coroutine contexts

Install `CoroutineLogContext` with `withContext` and call `withCoroutine()` in the logging DSL to merge the current coroutine values into the record.

```kotlin
import kotlinx.coroutines.withContext

suspend fun runJob(jobId: String) {
    val coroutineLogContext = CoroutineLogContext(
        mapOf("jobId" to jobId)
    )

    withContext(coroutineLogContext) {
        logger.info {
            withCoroutine()
            "job started"
        }
    }
}
```

On the JVM, combine both context elements when `ThreadLogContext` must also follow coroutine thread switches.

```kotlin
withContext(
    CoroutineThreadLogContext() +
            CoroutineLogContext(mapOf("jobId" to jobId))
) {
    logger.info {
        withCoroutine()
        "running on a coroutine"
    }
}
```

| API                                | Behavior                                                                                           |
|------------------------------------|----------------------------------------------------------------------------------------------------|
| `CoroutineLogContext(data)`        | Creates mutable log data and a coroutine context element.                                         |
| `CoroutineLogContext.get()`        | Returns the element installed in the current coroutine, or a new uninstalled empty element.       |
| `CoroutineLogContext.get(context)` | Finds the element in the supplied `CoroutineContext`.                                             |
| `LogProperties.withCoroutine()`    | Merges the coroutine context after the record context; coroutine values win.                      |
| `CoroutineThreadLogContext()`      | On the JVM, propagates `ThreadLogContext` across coroutine thread switches and restores it later. |

## `LogRecord` and metadata

`LogRecord` is the data contract processed by the pipeline.

| Property     | Type                | Description                                      |
|--------------|---------------------|--------------------------------------------------|
| `loggerName` | `String`            | The logger name.                                 |
| `level`      | `LogLevel`          | The log level.                                   |
| `body`       | `String`            | The log message body.                            |
| `exception`  | `Throwable?`        | An optional exception.                           |
| `eventName`  | `String?`           | An optional event name.                          |
| `meta`       | `Map<String, Any?>` | Structured data for this event.                  |
| `context`    | `LogContext`        | Global, thread-local, coroutine, or other context. |
| `threadName` | `String?`           | The thread name captured when the record was created. |
| `timestamp`  | `Instant`           | The creation time.                               |

`LogRecordData` is the default data-class implementation. When omitted, `context`, `threadName`, and `timestamp` are populated with the current context snapshot, current thread name, and current time. `record.isPrintableAt(level)` reports whether the record is printable at the configured level.

`SerializedLog.String` and `SerializedLog.ByteArray` retain the original `LogRecord` properties by delegation and store the formatted value in `serialized`. Use them when implementing a formatter pipe.

Use `asLogObject()` to serialize the accessible public properties supported by the bundled JSON formatters as a JSON object.

```kotlin
logger.error {
    exception = cause
    meta = mapOf(
        "exception" to cause.asLogObject(),
        "operation" to "charge",
    )
    "charge failed"
}
```

## Pipelines

`LogPipeline` passes a record to each `LogPipe` in installation order. When a pipe returns a new record, the next stage receives it. Returning `null` stops the rest of the pipeline.

```kotlin
Logger.pipeline = LogPipeline()
    .install(FilterPipe { record -> record.context["healthCheck"] != true })
    .install(LoggerNameShortener(preferLength = 30))
    .install(TextFormatter(printMeta = true, enableColor = true))
    .install(StdOutSink(printStackTrace = true, useStdErr = false))
```

Install serializers and formatters before `StdOutSink`. `StdOutSink` only prints `SerializedLog.String` records.

### Pipeline management

```kotlin
val pipeline = LogPipeline()
    .install(TextFormatter())
    .install(StdOutSink())
val customSink = SinkPipe { record -> println(record.body) }

pipeline.install(FilterPipe { it.level != LogLevel.TRACE }, index = 0)
pipeline.installBefore(MapPipe(::enrich), TextFormatter)
pipeline.installAfter(customSink, StdOutSink)

pipeline.isInstalled(TextFormatter)      // true
pipeline.installIndexOf(TextFormatter)   // -1 if absent
pipeline.uninstall(MapPipe)              // Remove every pipe with the same key
pipeline.uninstall(0)                    // Remove one pipe by index
pipeline.handle(record)                  // Run the pipeline directly
pipeline.clear()                         // Remove all pipes
```

| Function                   | Behavior                                                                                 |
|----------------------------|------------------------------------------------------------------------------------------|
| `install(pipe)`            | Appends the pipe and returns the same pipeline.                                          |
| `install(pipe, index)`     | Inserts the pipe at the given index; an invalid index throws an exception.               |
| `installBefore(pipe, key)` | Inserts the pipe before the first pipe with that key, or at index 0 when none exists.     |
| `installAfter(pipe, key)`  | Inserts the pipe after the last pipe with that key, or at index 0 when none exists.       |
| `uninstall(index)`         | Removes one pipe at the given index.                                                     |
| `uninstall(key)`           | Removes every pipe with the same key.                                                    |
| `isInstalled(key)`         | Returns whether at least one pipe with the key is installed.                             |
| `installIndexOf(key)`      | Returns the first installation index, or `-1` when absent.                              |
| `clear()`                  | Removes all pipes.                                                                       |
| `handle(record)`           | Processes a record from the beginning; normally called by `Logger.log`.                  |
| `clone()`                  | Creates a shallow copy of the pipeline that contains the same pipe instances.            |

### Built-in pipes

| Pipe                                                       | Purpose and options                                                                        |
|------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| `FilterPipe(predicate)`                                    | Passes only records for which the predicate returns `true`.                                |
| `MapPipe(transform)`                                       | Transforms a record into another `LogRecord`.                                               |
| `LoggerNameShortener(preferLength = 36)`                   | Shortens leading dot-separated name segments to one character; the target is not a strict maximum. |
| `TextFormatter(printMeta = true, enableColor = false)`     | Produces a text `SerializedLog.String`; context is not included.                           |
| `StdOutSink(printStackTrace = true, useStdErr = false)` | Prints serialized messages to stdout or stderr; optional stack traces are printed to stderr. |

`LoggerNameShortener` cannot be installed more than once with the same key and only transforms `LogRecordData` records.

The following example uses `MapPipe` to modify a record.

```kotlin
fun enrich(record: LogRecord): LogRecord =
    if (record is LogRecordData) {
        record.copy(meta = record.meta + ("environment" to "production"))
    } else {
        record
    }
```

### Custom pipes

```kotlin
class SinkPipe(
    private val sink: (LogRecord) -> Unit,
) : LogPipe {
    companion object Key : LogPipe.Key<SinkPipe>

    override val key: LogPipe.Key<out LogPipe> = Key

    override fun apply(record: LogRecord): LogRecord? {
        sink(record)
        return record // Return null to stop subsequent pipes.
    }
}

Logger.pipeline.installBefore(
    SinkPipe { record -> println("custom sink: ${record.body}") },
    TextFormatter,
)
```

Override `LogPipe.installTo(pipeline, index)` to change a pipe's installation policy. The default implementation inserts it at the exact index. `apply(record)` is invoked by the pipeline and normally does not need to be called directly.

## JVM JSON formatters

The Gson and Jackson formatters are provided by separate JVM integration modules. Add the corresponding artifact from [Installation](#installation), then import the formatter from its package.

### Gson

```kotlin
import kim.jade.kotlinx.logger.integration.gson.GsonFormatter

Logger.pipeline = LogPipeline()
    .install(LoggerNameShortener())
    .install(GsonFormatter(traceLimit = 12))
    .install(StdOutSink(printStackTrace = false))
```

### Jackson 3

```kotlin
import kim.jade.kotlinx.logger.integration.jackson.JacksonFormatter

Logger.pipeline = LogPipeline()
    .install(LoggerNameShortener())
    .install(JacksonFormatter(traceLimit = 12))
    .install(StdOutSink(printStackTrace = false))
```

| Option                            | Description                                                                                              |
|-----------------------------------|----------------------------------------------------------------------------------------------------------|
| First argument, `gson` / `mapper` | Builds the formatter from a custom Gson instance or Jackson `ObjectMapper` configuration.                |
| `traceLimit = 12`                 | Limits the number of frames in an exception stack trace; use `Int.MAX_VALUE` for the full trace.         |
| `useCustomDateSerializer = false` | Serializes `Instant` as epoch milliseconds by default; set to `true` to use the supplied serializer configuration. |

If Gson serialization fails, the formatter falls back to an error message and the `TextFormatter` result. Jackson serialization exceptions are propagated to the caller.

## JVM logging interoperability

Add the `kotlinx-logger-integration-jvm` artifact to use the JUL and SLF4J integrations in this section.

### SLF4J 2

The JVM integration artifact includes an SLF4J service provider for standard `LoggerFactory` logging. Add a compatible 2.x release of `org.slf4j:slf4j-api` as a direct dependency if your application does not already provide it. The basic `MDC` key/value functions are backed by `ThreadLogContext`.

```kotlin
import org.slf4j.LoggerFactory
import org.slf4j.MDC

val slf4jLogger = LoggerFactory.getLogger("legacy-component")

try {
    MDC.put("requestId", requestId)
    slf4jLogger.info("received order {}", orderId)
} finally {
    MDC.clear()
}
```

You can also instantiate `KotlinxLoggerAdapter` with either a core `Logger` or a name, or create `KotlinxLoggerFactory` and `KotlinxLoggerMdcAdapter` directly. `LoggerFactory` and `MDC` normally load them automatically. Avoid placing another SLF4J provider on the same classpath.

### java.util.logging (JUL)

When `kotlinx-logger-integration-jvm` is on the classpath, the core `Logger` discovers the JUL initializer during first initialization and adds a `JulLogger` handler to the root JUL logger. JUL records are then passed to the same `LogPipeline`. Existing root handlers are retained and may print the same records, so adjust the root handler configuration if you want to avoid duplicate output. JUL logger and handler levels still determine which records reach the pipeline.

```kotlin
Logger.named("bootstrap") // Initialize the core logger and load the JUL integration

val julLogger = java.util.logging.Logger.getLogger("legacy-component")
julLogger.info("message from JUL")
```

## Integration modules

### Koin

```kotlin
import kim.jade.kotlinx.logger.integration.koin.KoinLogger
import org.koin.core.context.startKoin

startKoin {
    logger(KoinLogger())
    modules(appModule)
}
```

The default core logger name used by `KoinLogger()` is `KoinApplication`. You can also supply a logger explicitly.

```kotlin
logger(KoinLogger(Logger.named("dependency-injection")))
```

### Ktor server

#### Request `LogContext`

```kotlin
import io.ktor.server.application.install
import io.ktor.server.request.header
import kim.jade.kotlinx.logger.integration.ktor.LogContext as KtorLogContext

install(KtorLogContext) {
    setupContext = { context ->
        context["tenantId"] = request.header("X-Tenant-Id")
    }
}
```

The plugin adds the call ID, HTTP method, path, and route to `CoroutineLogContext` and runs request handling in that coroutine context. The default `setupContext` also adds `remoteAddress`, `userAgent`, and `headers`; the current `remoteAddress` value comes from `request.host()`. Assigning a new lambda to `setupContext`, as above, replaces the default lambda, so add any required default values yourself.

#### `RequestLogger`

```kotlin
import io.ktor.server.application.install
import io.ktor.server.request.path
import kim.jade.kotlinx.logger.integration.ktor.RequestLogger

install(RequestLogger) {
    logger = Logger.named("http-server")
    logLevel = { call ->
        if (call.request.path() == "/health") null else LogLevel.INFO
    }
    canLogBody = { false }
    additionalMeta = { meta ->
        meta["host"] = request.host()
    }
}
```

When a response is sent, the plugin logs the status, method, path, and processing time, and adds path parameters and query parameters to `meta`. For `302 Found` responses, it also appends the `Location` header to the message. Requests for which `logLevel` returns `null` are not logged.

Use the following functions for per-route control:

```kotlin
routing {
    get("/health") {
        disableRequestLog()
        call.respondText("ok")
    }

    post("/debug/users") {
        enableRequestLog()
        logBody(true)
        // ...
    }
}
```

| Function or setting     | Behavior                                                               |
|-------------------------|------------------------------------------------------------------------|
| `enableRequestLog()`    | Explicitly enables request logging for the current route.              |
| `disableRequestLog()`   | Disables request logging for the current route.                        |
| `logBody(enable)`       | Overrides body logging for the current request.                        |
| `logger`                | The core `Logger` to use; the default name is `RequestLogger`.         |
| `logLevel(call)`        | Returns the level for each request; `null` skips the log.              |
| `canLogBody()`          | The global body-logging condition; defaults to `false`.                |
| `additionalMeta(meta)`  | Adds metadata for each request.                                        |

The body is read only for POST, PUT, or PATCH requests whose content type is JSON or form URL-encoded, and only when `logBody(true)` or `canLogBody` permits it. Restrict body and header logging carefully to avoid recording passwords, tokens, or personal data.

### OkHttp

#### Official `HttpLoggingInterceptor` adapter

```kotlin
import kim.jade.kotlinx.logger.integration.okhttp.OkHttpLogInterceptorFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

Logger.level = LogLevel.DEBUG

val client = OkHttpClient.Builder()
    .addInterceptor(
        OkHttpLogInterceptorFactory.create(
            clientName = "backend",
            logLevel = LogLevel.DEBUG,
            interceptLevel = HttpLoggingInterceptor.Level.BASIC,
        )
    )
    .build()
```

This adapter forwards the text lines produced by OkHttp's official interceptor to kotlinx-logger at the selected `LogLevel`.

#### Structured interceptor

Use `OkHttpLogger` when you want request and response details recorded as structured metadata.

```kotlin
import kim.jade.kotlinx.logger.LogLevel
import kim.jade.kotlinx.logger.integration.okhttp.OkHttpLogger
import okhttp3.OkHttpClient

val structuredClient = OkHttpClient.Builder()
    .addInterceptor(
        OkHttpLogger(
            clientName = "backend",
            option = OkHttpLogger.HttpLogOption(
                successLogLevel = LogLevel.INFO,
                includeRequestHeaders = true,
                includeResponseHeaders = true,
            ),
        )
    )
    .build()
```

`OkHttpLogger.HttpLogOption` controls success and failure levels, header and body capture, and whether request and response data are combined in one record. Header and body capture are disabled by default; enable them only when sensitive values are excluded. In the current implementation, enable `includeResponseBody` only for requests that have a non-null request body. Attach a `LogContext` to an individual request with an OkHttp tag; `OkHttpLogger` merges it with the current global and thread-local context snapshot.

```kotlin
val request = Request.Builder()
    .url(url)
    .tag(
        LogContext::class.java,
        LogContext(mapOf("requestId" to requestId)),
    )
    .build()
```

You can also convert HTTP data directly into `LogRecordData` with `HttpRequestLog.toLogRecord(loggerName, logLevel, context)` and `HttpResponseLog.toLogRecord(loggerName, logLevel, context, withRequest)`. `HttpResponseLog.duration` is the difference between the request and response timestamps.

### Sentry

Initialize Sentry before installing `SentrySink`.

```kotlin
import io.sentry.Sentry
import kim.jade.kotlinx.logger.integration.sentry.SentrySink

Sentry.init { options ->
    options.dsn = System.getenv("SENTRY_DSN")
}

Logger.pipeline.install(SentrySink())
```

By default, `SentrySink` forwards records at `WARNING` or a more severe level. Pass a custom `isAcceptable` predicate to change the threshold.

### OpenTelemetry

`kotlinx-logger-integration-opentelemetry` is a JVM module that provides bridges in both directions between kotlinx-logger and the OpenTelemetry Logs API.

#### From kotlinx-logger to OpenTelemetry

Initialize the OpenTelemetry SDK, then install `OpenTelemetrySink`. Its constructor accepts either `OpenTelemetry` or an OpenTelemetry `LoggerProvider`.

```kotlin
import kim.jade.kotlinx.logger.integration.opentelemetry.OpenTelemetrySink

//val openTelemetry = ...

Logger.pipeline.install(OpenTelemetrySink(openTelemetry))
```

`OpenTelemetrySink` forwards the logger name, severity, timestamps, body, exception, and event name. The current implementation does not map `LogRecord.meta` or `LogRecord.context` to OpenTelemetry attributes.

#### From OpenTelemetry to kotlinx-logger

Use `OTelKotlinxLoggerProvider` as an OpenTelemetry `LoggerProvider` to pass records created with the OpenTelemetry Logs API to the core `Logger` and `LogPipeline`.

```kotlin
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.logs.Severity
import kim.jade.kotlinx.logger.integration.opentelemetry.OTelKotlinxLoggerProvider

val otelLogger = OTelKotlinxLoggerProvider()
    .loggerBuilder("checkout")
    .setInstrumentationVersion("1.0.0")
    .build()

otelLogger.logRecordBuilder()
    .setSeverity(Severity.INFO)
    .setEventName("order.created")
    .setBody("order created")
    .setAttribute(AttributeKey.stringKey("order.id"), orderId)
    .emit()
```

**Do not connect the two bridges to each other.** If an `OpenTelemetrySink` in the core pipeline targets an `OTelKotlinxLoggerProvider`, each record is sent back into the same pipeline, causing recursive calls.

## License

Distributed under the Apache License 2.0. See [LICENSE](LICENSE) for details.
