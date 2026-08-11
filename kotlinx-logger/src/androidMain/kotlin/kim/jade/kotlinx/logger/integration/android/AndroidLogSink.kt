package kim.jade.kotlinx.logger.integration.android

import android.util.Log
import kim.jade.kotlinx.logger.LogLevel
import kim.jade.kotlinx.logger.LogRecord
import kim.jade.kotlinx.logger.pipeline.LogPipe
import kim.jade.kotlinx.logger.pipeline.StdOutSink
import kim.jade.kotlinx.logger.util.escapedForLog

class AndroidLogSink(
    printStackTrace: Boolean = true,
    var escapeControlChars: Boolean = true,
) : StdOutSink(printStackTrace, useStdErr = false) {

    override val key: LogPipe.Key<out LogPipe> = Key

    override fun apply(record: LogRecord, next: (LogRecord) -> Unit) {
        val eventName = record.eventName
        val message = if (eventName == null) record.body.escaped() else {
            "${eventName.escaped()}: ${record.body.escaped()}"
        }

        Log.println(record.level.toAndroidLogLevel(), record.loggerName.escaped(), message)

        if (printStackTrace) {
            record.exception?.printStackTrace()
        }

        next(record)
    }

    private fun String.escaped(): String = if (escapeControlChars) escapedForLog() else this

    private fun LogLevel.toAndroidLogLevel(): Int = when (this) {
        LogLevel.NONE -> Log.VERBOSE
        LogLevel.TRACE -> Log.VERBOSE
        LogLevel.DEBUG -> Log.DEBUG
        LogLevel.INFO -> Log.INFO
        LogLevel.WARNING -> Log.WARN
        LogLevel.ERROR -> Log.ERROR
        LogLevel.FATAL -> Log.ASSERT
    }
}
