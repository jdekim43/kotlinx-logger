package kr.jadekim.logger.integration.ktor

import io.ktor.server.application.*
import io.ktor.server.plugins.callid.callId
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.withContext
import kr.jadekim.logger.context.MutableLogContext
import kr.jadekim.logger.coroutine.context.CoroutineLogContext

class JLogContextConfiguration {
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
        val hookPhase = PipelinePhase("${phase.name}JLogContext")
        pipeline.insertPhaseBefore(phase, hookPhase)

        pipeline.intercept(hookPhase) {
            handler(call, ::proceed)
        }
    }
}

internal val RouteKey = AttributeKey<MutableLogContext>("JLogContext")

val JLogContext = createApplicationPlugin("JLogContext", { JLogContextConfiguration() }) {
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
