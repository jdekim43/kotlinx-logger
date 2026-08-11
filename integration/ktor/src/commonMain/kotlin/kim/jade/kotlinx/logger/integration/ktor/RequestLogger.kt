@file:Suppress("unused")

package kim.jade.kotlinx.logger.integration.ktor

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kim.jade.kotlinx.logger.LogLevel
import kim.jade.kotlinx.logger.Logger
import kotlin.time.Clock
import kotlin.time.DurationUnit
import kotlin.time.Instant

val REQUEST_LOG_ENABLE = AttributeKey<Boolean>("requestLog.enable")
val REQUEST_LOG_BODY = AttributeKey<Boolean>("requestLog.printBody")

fun RoutingContext.enableRequestLog() {
    call.attributes.put(REQUEST_LOG_ENABLE, true)
}

fun RoutingContext.disableRequestLog() {
    call.attributes.put(REQUEST_LOG_ENABLE, false)
}

fun RoutingContext.logBody(enable: Boolean) {
    call.attributes.put(REQUEST_LOG_BODY, enable)
}

class RequestLoggerConfiguration {
    var additionalMeta: ApplicationCall.(MutableMap<String, Any?>) -> Unit = {}
    var canLogBody: ApplicationCall.() -> Boolean = { false }
    var logger: Logger = Logger.named("RequestLogger")

    var maxBodyLength: Int = DEFAULT_MAX_BODY_LENGTH

    // if null, would be not logged.
    var logLevel: (ApplicationCall) -> LogLevel? = { LogLevel.INFO }

    companion object {
        const val DEFAULT_MAX_BODY_LENGTH: Int = 8 * 1024
    }
}

private fun Hook(phase: PipelinePhase) = object : Hook<suspend (ApplicationCall, suspend () -> Unit) -> Unit> {

    override fun install(
        pipeline: ApplicationCallPipeline,
        handler: suspend (ApplicationCall, suspend () -> Unit) -> Unit
    ) {
        val hookPhase = PipelinePhase("RequestLogger${phase.name}Phase")
        pipeline.insertPhaseBefore(phase, hookPhase)

        pipeline.intercept(hookPhase) {
            handler(call, ::proceed)
        }
    }
}

private object ResponseHook : Hook<suspend (ApplicationCall) -> Unit> {

    override fun install(
        pipeline: ApplicationCallPipeline,
        handler: suspend (ApplicationCall) -> Unit
    ) {
        val hookPhase = PipelinePhase("RequestLoggerResponsePhase")
        pipeline.sendPipeline.insertPhaseAfter(ApplicationSendPipeline.Engine, hookPhase)

        pipeline.sendPipeline.intercept(hookPhase) {
            handler(call)
        }
    }
}

val RequestLogger = createApplicationPlugin("RequestLogger", { RequestLoggerConfiguration() }) {

    val processStartTime = AttributeKey<Instant>("RequestLogger.processStartTime")

    on(CallSetup) { call ->
        call.attributes.put(processStartTime, Clock.System.now())
    }

    on(ResponseHook) { call ->
        if (call.attributes.getOrNull(REQUEST_LOG_ENABLE) == false) return@on
        val logLevel = pluginConfig.logLevel(call) ?: return@on
        if (!logLevel.isPrintableAt(pluginConfig.logger.level)) return@on

        val meta = mutableMapOf<String, Any?>()

        meta["pathParameter"] = call.parameters.toValueMap()
        meta["query"] = call.request.queryParameters.toValueMap()

        if (call.request.httpMethod.readableBody
            && call.request.contentType().readableBody
            && call.attributes.getOrNull(REQUEST_LOG_BODY) ?: pluginConfig.canLogBody(call)
        ) {
            meta["body"] = call.loggableBody(pluginConfig.maxBodyLength)
        }

        pluginConfig.additionalMeta(call, meta)

        val status = call.response.status()
        val duration = Clock.System.now() - call.attributes[processStartTime]
        var logMessage =
            "$status: ${call.request.httpMethod.value} - ${call.request.path()} in ${duration.toString(DurationUnit.MILLISECONDS)}"

        if (status == HttpStatusCode.Found) {
            logMessage += " -> ${call.response.headers[HttpHeaders.Location]}"
        }

        pluginConfig.logger.log(logLevel) {
            withCoroutine()

            this.meta = meta
            logMessage
        }
    }
}

private fun Parameters.toValueMap(): Map<String, Any?> = entries().associate { (name, values) ->
    name to if (values.size == 1) values.single() else values
}

private suspend fun ApplicationCall.loggableBody(limit: Int): String = try {
    val text = receiveText()

    if (text.length > limit) "${text.take(limit)}… (truncated at $limit chars)" else text
} catch (e: Exception) {
    "Unavailable (${e::class.simpleName})"
}

private val HttpMethod.readableBody
    get() = when (this) {
        HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch -> true
        else -> false
    }

private val ContentType.readableBody
    get() = when (this) {
        ContentType.Application.Json, ContentType.Application.FormUrlEncoded -> true
        else -> false
    }