# j-logger

## Feature
* Support Multiplatform
  * Kotlin/JVM
  * Java (Beta)
  * JS (Alpha)
* Contextual logging
  * GlobalLogContext
  * ThreadLogContext
  * CoroutineLogContext
* Customizable log pipeline : (e.g. filtering, re-format, parallel processing)
* Integration
  * Coroutine
  * Ktor (Only JVM - Dependent by ktor)
  * Koin
  * slf4j (Only JVM)
  * OkHttp (Only JVM)
  * Fuel (Only JVM)
* Configuration option
  * Code
  * yaml or properties (TO-DO)
  * filtering with tag or namespace (TO-DO)

## Install
### Gradle Project
1. Add dependency
    ```
    build.gradle.kts
   
    implementation("kr.jadekim:j-logger:$jLoggerVersion")
    ```
## How to use
### Create logger
```
val logger = JLog.get("loggerName")

//In JVM
val logger = JLog.get(A::class)
val logger = JLog.get(A::class.java)
```
### Log
```
logger.trace("trace log", meta = mapOf())
logger.debug("debug log", meta = mapOf())
logger.info("info log", meta = mapOf())
logger.warning("warning log", meta = mapOf())
logger.error("error log", meta = mapOf())

try {
    //occur exception
} catch (e: Exception) {
    logger.error("Occur Exception", throwable = e, meta = mapOf())
}
```
### LogContext
LogContext can upsert using plus operation.
```
val logContext = LogContext(mapOf())
val logContext = MutableLogContext(mutableMapOf())

GlobalLogContext["globalContext"] = "global context"

ThreadLogContext["threadLocalContext"] = "thread local context"

logger.info("info log") //LogContext = GlobalLogContext + ThreadLogContext
logger.info("info log", context = logContext) //LogContext = GlobalLogContext + ThreadLogContext + logContext

withContext(CoroutineLogContext() + logContext) {
    //LogContext = GlobalLogContext + ThreadLogContext + CoroutineLogContext + logContext
    logger.sTrace("trace log", meta = mapOf())
    logger.sDebug("debug log", meta = mapOf())
    logger.sInfo("info log", meta = mapOf())
    logger.sWarning("warning log", meta = mapOf())
    logger.sError("error log", meta = mapOf())
}
```

## Pipeline
### Implement
```
class JLogExamplePipe : JLogPipe {
    companion object Key : JLogPipe.Key<JLogExamplePipe>
    
    override val key = Key
    
    override fun handle(log: Log): Log? {
        // if return null, the log will be filtered.
        // Log is interface. So, We can return any type that implemented Log interface.
        // But must be support return type to pipeline after this.
    }
}
```