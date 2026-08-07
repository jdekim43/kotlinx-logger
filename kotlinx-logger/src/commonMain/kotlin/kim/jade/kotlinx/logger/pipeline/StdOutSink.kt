package kim.jade.kotlinx.logger.pipeline

import kim.jade.kotlinx.io.eprintln
import kim.jade.kotlinx.logger.LogRecord
import kim.jade.kotlinx.logger.SerializedLog

open class StdOutSink(
    var printStackTrace: Boolean = true,
    var useStdErr: Boolean = false,
) : LogPipe {

    companion object Key : LogPipe.Key<StdOutSink>

    override val key: LogPipe.Key<out LogPipe> = Key

    override fun apply(record: LogRecord, next: (LogRecord) -> Unit) {
        if (record is SerializedLog<*> && record.serialized is String) {
            if (useStdErr) {
                eprintln(record.serialized)
            } else {
                println(record.serialized)
            }
        } else {
            eprintln("ERROR: StdOutSink is only acceptable SerializedLog<String>. Require to install TextFormatter before printer")
        }

        if (printStackTrace) {
            record.exception?.printStackTrace()
        }

        next(record)
    }
}
