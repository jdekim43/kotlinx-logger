package kim.jade.kotlinx.logger.integration.slf4j

import org.slf4j.ILoggerFactory
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class KotlinxLoggerFactory : ILoggerFactory {

    private val loggerMap: ConcurrentMap<String, KotlinxLoggerAdapter> = ConcurrentHashMap()

    override fun getLogger(name: String): Logger = loggerMap.getOrPut(name) { KotlinxLoggerAdapter(name) }
}