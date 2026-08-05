@file:Suppress("unused")

package kim.jade.kotlinx.logger.integration.ktor

import io.ktor.server.application.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kim.jade.kotlinx.logger.context.CoroutineLogContext
import kim.jade.kotlinx.logger.context.MutableLogContext
import kotlinx.coroutines.withContext

class LogContextConfiguration {
    var setupContext: ApplicationCall.(MutableLogContext) -> Unit = {
        it["remoteAddress"] = request.host()
        it["userAgent"] = request.userAgent()
        it["headers"] = request.headers.toMap()
    }
}

private fun Hook(phase: PipelinePhase) = object : Hook<suspend (ApplicationCall, suspend () -> Unit) -> Unit> {

    override fun install(
        pipeline: ApplicationCallPipeline,
        handler: suspend (ApplicationCall, suspend () -> Unit) -> Unit
    ) {
        val hookPhase = PipelinePhase("${phase.name}LogContext")
        pipeline.insertPhaseBefore(phase, hookPhase)

        pipeline.intercept(hookPhase) {
            handler(call, ::proceed)
        }
    }
}

internal val RouteKey = AttributeKey<MutableLogContext>("LogContext")

val LogContext = createApplicationPlugin("LogContext", { LogContextConfiguration() }) {
    fun MutableLogContext.setContextsFrom(call: ApplicationCall) {
        this["callId"] = call.callId
        this["method"] = call.request.httpMethod
        this["path"] = call.request.path()

        pluginConfig.setupContext(call, this)
    }

    application.monitor.subscribe(RoutingRoot.RoutingCallStarted) { call ->
        call.attributes.getOrNull(RouteKey)?.set("route", call.route.toString())
    }

    on(Hook(ApplicationCallPipeline.Monitoring)) { call, proceed ->
        val logContext = CoroutineLogContext.get()
        logContext.setContextsFrom(call)

        call.attributes.put(RouteKey, logContext)

        withContext(logContext) {
            proceed()
        }
    }

    on(Hook(ApplicationCallPipeline.Call)) { call, proceed ->
        val logContext = CoroutineLogContext.get()

        logContext["route"] = when (call) {
            is RoutingCall -> call.route.toString()
            is RoutingPipelineCall -> call.route.toString()
            else -> "${call.request.path()}/(method:${call.request.httpMethod.value})"
        }

        logContext.setContextsFrom(call)

        withContext(logContext) {
            proceed()
        }
    }
}
