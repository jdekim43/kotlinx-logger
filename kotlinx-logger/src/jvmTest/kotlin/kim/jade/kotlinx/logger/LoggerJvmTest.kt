package kim.jade.kotlinx.logger

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kim.jade.kotlinx.logger.context.CoroutineThreadLogContext
import kim.jade.kotlinx.logger.context.MutableLogContext
import kim.jade.kotlinx.logger.context.ThreadLogContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

private class JavaTypedLoggerFixture

private const val STACK_KEY = "coroutine-stack-test"

class LoggerJvmTest : FunSpec({

    context("Java typed factory") {
        test("uses the canonical name and the shared logger cache") {
            val logger = Logger.typed(JavaTypedLoggerFixture::class.java)

            logger.name shouldBe JavaTypedLoggerFixture::class.java.canonicalName
            logger shouldBeSameInstanceAs Logger.named(JavaTypedLoggerFixture::class.java.canonicalName)
        }
    }

    context("hierarchical configuration under concurrency") {
        test("readers converge on the last write and never fail") {
            val ancestor = Logger.configure("hier-concurrent.a") { level = LogLevel.WARNING }
            val readers = (0 until 4).map { reader ->
                Logger.named("hier-concurrent.a.b.c.reader$reader")
            }

            try {
                coroutineScope {
                    val writer = async(Dispatchers.Default) {
                        repeat(500) { ancestor.level = if (it % 2 == 0) LogLevel.TRACE else LogLevel.DEBUG }
                        ancestor.level = LogLevel.ERROR
                    }
                    val reads = readers.map { logger ->
                        async(Dispatchers.Default) { List(500) { logger.level } }
                    }

                    writer.await()
                    reads.forEach { it.await() }
                }

                readers.forEach { it.level shouldBe LogLevel.ERROR }
            } finally {
                Logger.resetAllConfiguration()
            }
        }
    }

    context("CoroutineThreadLogContext") {
        lateinit var savedMap: Map<String, Any?>
        var savedStack: List<Any?>? = null

        beforeTest {
            savedMap = ThreadLogContext.toMap()
            savedStack = ThreadLogContext.copyAllInStack(STACK_KEY)
            ThreadLogContext.clear()
            ThreadLogContext.clearStack(STACK_KEY)
        }

        afterTest {
            ThreadLogContext.clear()
            ThreadLogContext.putAll(savedMap)
            ThreadLogContext.clearStack(STACK_KEY)
            savedStack?.forEach { value -> value?.let { ThreadLogContext.push(STACK_KEY, it) } }
        }

        test("installs a copy of its values and restores the previous thread context") {
            ThreadLogContext["scope"] = "outside"
            val propagated = MutableLogContext(mapOf("scope" to "inside", "requestId" to "request-8"))

            withContext(Dispatchers.Default + CoroutineThreadLogContext(propagated)) {
                ThreadLogContext shouldContainExactly mapOf(
                    "scope" to "inside",
                    "requestId" to "request-8",
                )
                ThreadLogContext["insideOnly"] = true
            }

            ThreadLogContext shouldContainExactly mapOf("scope" to "outside")
            propagated shouldContainExactly mapOf(
                "scope" to "inside",
                "requestId" to "request-8",
            )
        }

        test("propagates and restores thread context stacks") {
            ThreadLogContext.push(STACK_KEY, "outside")

            withContext(Dispatchers.Default + CoroutineThreadLogContext()) {
                requireNotNull(ThreadLogContext.copyAllInStack(STACK_KEY)) shouldBe listOf("outside")

                ThreadLogContext.push(STACK_KEY, "inside")
                requireNotNull(ThreadLogContext.copyAllInStack(STACK_KEY)) shouldBe listOf("outside", "inside")
            }

            requireNotNull(ThreadLogContext.copyAllInStack(STACK_KEY)) shouldBe listOf("outside")
        }

        test("isolates stack mutations between child coroutines") {
            ThreadLogContext.push(STACK_KEY, "parent")

            withContext(Dispatchers.Default + CoroutineThreadLogContext()) {
                coroutineScope {
                    val firstPushed = CompletableDeferred<Unit>()
                    val secondPushed = CompletableDeferred<Unit>()
                    val first = async(Dispatchers.Default) {
                        ThreadLogContext.push(STACK_KEY, "first")
                        firstPushed.complete(Unit)
                        secondPushed.await()
                        requireNotNull(ThreadLogContext.copyAllInStack(STACK_KEY))
                    }
                    val second = async(Dispatchers.Default) {
                        ThreadLogContext.push(STACK_KEY, "second")
                        secondPushed.complete(Unit)
                        firstPushed.await()
                        requireNotNull(ThreadLogContext.copyAllInStack(STACK_KEY))
                    }

                    first.await() shouldBe listOf("parent", "first")
                    second.await() shouldBe listOf("parent", "second")
                }

                requireNotNull(ThreadLogContext.copyAllInStack(STACK_KEY)) shouldBe listOf("parent")
            }

            requireNotNull(ThreadLogContext.copyAllInStack(STACK_KEY)) shouldBe listOf("parent")
        }
    }
})
