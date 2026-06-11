package kr.jadekim.logger

import co.touchlab.stately.collections.SharedHashMap
import kr.jadekim.logger.option.JLoggerOptionProvider
import kr.jadekim.logger.pipeline.JLogPipe
import kr.jadekim.logger.pipeline.LoggerNameShorter
import kr.jadekim.logger.pipeline.StdOutPrinter
import kr.jadekim.logger.pipeline.TextFormatter

internal expect fun init()

object JLog {

    var loggerLevel = LogLevel.INFO
    var pipeline: MutableList<JLogPipe> = mutableListOf(
        LoggerNameShorter(),
        TextFormatter(),
        StdOutPrinter(),
    )

    var optionProvider = JLoggerOptionProvider.builder().build()

    private val loggers = SharedHashMap<String, JLogger>()

    init {
        init()
    }

    fun get(name: String): JLogger = loggers.getOrPut(name) {
        val option = optionProvider[name]

        JLogger(name, option.level, option.pipeline)
    }

    fun installPipe(vararg pipe: JLogPipe) {
        pipe.forEach { it.install(pipeline, pipeline.size) }
    }

    fun <Pipe : JLogPipe> installPipeBefore(reference: JLogPipe.Key<Pipe>, pipe: Pipe) {
        var index = pipeline.indexOfFirst { it == reference }

        if (index == -1) {
            index = 0
        }

        pipe.install(pipeline, index)
    }

    fun <Pipe : JLogPipe> installPipeAfter(reference: JLogPipe.Key<Pipe>, pipe: Pipe) {
        var index = pipeline.indexOfFirst { it.key == reference }

        if (index == -1) {
            index = pipeline.size
        }

        pipe.install(pipeline, index)
    }

    fun <Pipe : JLogPipe> uninstallPipe(pipeKey: JLogPipe.Key<Pipe>) {
        pipeline.removeAll { it.key == pipeKey }
    }
}