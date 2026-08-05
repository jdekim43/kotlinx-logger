package kim.jade.log

enum class LogLevel(val logName: String) {
    NONE("NONE"),
    FATAL("FATAL"),
    ERROR("ERROR"),
    WARNING("WARN"),
    INFO("INFO"),
    DEBUG("DEBUG"),
    TRACE("TRACE");

    fun isPrintableAt(level: LogLevel) = ordinal <= level.ordinal
}