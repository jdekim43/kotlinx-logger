package kr.jadekim.logger.pipeline

import kr.jadekim.logger.Log
import kr.jadekim.logger.SerializedLog

internal expect fun eprintln(text: String)

class StdOutPrinter(
    val printStackTrace: Boolean = true,
    val useStdErr: Boolean = true,
) : JLogPipe {

    companion object Key : JLogPipe.Key<StdOutPrinter>

    override val key = Key

    override fun install(pipeline: MutableList<JLogPipe>, index: Int) {
        if (pipeline.any { StdOutPrinter::class.isInstance(it) }) {
            return
        }

        pipeline.add(index, this)
    }

    override fun handle(log: Log): Log {
        when (log) {
            is SerializedLog.LogString -> {
                if (useStdErr) {
                    eprintln(log.data)
                } else {
                    println(log.data)
                }
            }
            else -> eprintln("ERROR: StdOutPrinter is only acceptable SerializedLog.LogString. Require to install TextFormatter before printer")
        }

        if (printStackTrace) {
            log.throwable?.printStackTrace()
        }

        return log
    }
}
