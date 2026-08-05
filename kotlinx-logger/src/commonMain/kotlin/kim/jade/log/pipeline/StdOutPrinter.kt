package kim.jade.log.pipeline

import kim.jade.kotlinx.io.eprintln
import kim.jade.log.LogRecord
import kim.jade.log.SerializedLog

class StdOutPrinter(
    var printStackTrace: Boolean = true,
    var useStdErr: Boolean = false,
) : LogPipe {

    companion object Key : LogPipe.Key<StdOutPrinter>

    override val key: LogPipe.Key<out LogPipe> = Key

    override fun apply(record: LogRecord): LogRecord {
        if (record is SerializedLog<*> && record.serialized is String) {
            if (useStdErr) {
                eprintln(record.serialized)
            } else {
                println(record.serialized)
            }
        } else {
            eprintln("ERROR: StdOutPrinter is only acceptable SerializedLog<String>. Require to install TextFormatter before printer")
        }

        if (printStackTrace) {
            record.exception?.printStackTrace()
        }

        return record
    }
}