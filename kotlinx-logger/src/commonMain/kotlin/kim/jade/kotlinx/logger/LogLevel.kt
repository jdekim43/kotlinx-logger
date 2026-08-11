package kim.jade.kotlinx.logger

enum class LogLevel(val logName: String) {
    FATAL("FATAL"),
    ERROR("ERROR"),
    WARNING("WARN"),
    INFO("INFO"),
    DEBUG("DEBUG"),
    TRACE("TRACE"),
    NONE("NONE");

    fun isPrintableAt(level: LogLevel) = ordinal <= level.ordinal
}