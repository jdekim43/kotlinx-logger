package kim.jade.kotlinx.logger

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kim.jade.kotlinx.logger.context.CoroutineLogContext
import kim.jade.kotlinx.logger.context.CoroutineThreadLogContext
import kim.jade.kotlinx.logger.context.GlobalLogContext
import kim.jade.kotlinx.logger.context.MutableLogContext
import kim.jade.kotlinx.logger.context.ThreadLogContext
import kim.jade.kotlinx.logger.context.snapCurrentLogContext
import kim.jade.kotlinx.logger.context.withThreadLogContext
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

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

            withContext(Dispatchers.Default + CoroutineThreadLogContext(propagated, null)) {
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

        test("propagates thread fields independently across dispatcher switches") {
            ThreadLogContext.reset()
            val logContext = CoroutineLogContext(mapOf("scope" to "request", "status" to "pending"))
            val global = GlobalLogContext.toMap()
            GlobalLogContext["scope"] = "global"
            GlobalLogContext["attempt"] = 0
            GlobalLogContext["globalOnly"] = true

            try {
                Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
                    withContext(CoroutineThreadLogContext(includeCoroutineContext = false) + logContext) {
                        ThreadLogContext["scope"] shouldBe null
                        ThreadLogContext["scope"] = "thread"
                        ThreadLogContext["attempt"] = 1

                        withContext(dispatcher) {
                            CoroutineLogContext.get()["attempt"] shouldBe null
                            CoroutineLogContext.get()["status"] = "done"
                            ThreadLogContext["status"] shouldBe null
                            val snapshot = snapCurrentLogContext()
                            snapshot["scope"] shouldBe "thread"
                            snapshot["attempt"] shouldBe 1
                            snapshot["status"] shouldBe null
                            snapshot["globalOnly"] shouldBe true
                        }
                    }
                }

                snapCurrentLogContext()["status"] shouldBe null
                ThreadLogContext.isEmpty() shouldBe true
            } finally {
                GlobalLogContext.clear()
                GlobalLogContext.putAll(global)
            }
        }

        test("preserves the thread snapshot across a nested coroutine context") {
            ThreadLogContext["scope"] = "thread"
            val outer = CoroutineLogContext(mapOf("scope" to "outer"))
            val inner = CoroutineLogContext(mapOf("scope" to "inner"))

            withContext(CoroutineThreadLogContext() + outer) {
                withContext(inner) {
                    snapCurrentLogContext()["scope"] shouldBe "thread"
                    CoroutineLogContext.get()["scope"] shouldBe "inner"
                    CoroutineLogContext.get()["innerOnly"] = true
                }

                snapCurrentLogContext()["scope"] shouldBe "thread"
                CoroutineLogContext.get()["innerOnly"] shouldBe null
                CoroutineLogContext.get()["outerOnly"] = true
            }

            outer["outerOnly"] shouldBe true
            inner["innerOnly"] shouldBe true
        }

        test("keeps coroutine fields when thread fields are temporarily replaced or cleared") {
            val logContext = CoroutineLogContext(mapOf("scope" to "request"))

            withContext(CoroutineThreadLogContext() + logContext) {
                withThreadLogContext("scope" to "temporary") {
                    ThreadLogContext["scope"] shouldBe "temporary"
                    snapCurrentLogContext()["scope"] shouldBe "temporary"
                }

                ThreadLogContext.clear()
                ThreadLogContext["scope"] shouldBe null
                CoroutineLogContext.get()["scope"] shouldBe "request"
                snapCurrentLogContext()["scope"] shouldBe null
            }
        }

        test("includes current coroutine fields by default across dispatcher switches and child launches") {
            ThreadLogContext["scope"] = "outside"
            val request = CoroutineLogContext(mapOf("requestId" to "request-1"))

            Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
                withContext(CoroutineThreadLogContext() + request) {
                    ThreadLogContext["requestId"] shouldBe "request-1"
                    request["route"] = "/orders"
                    ThreadLogContext.toMap() shouldContainExactly mapOf(
                        "requestId" to "request-1", "route" to "/orders", "scope" to "outside",
                    )

                    withContext(dispatcher + CoroutineLogContext(mapOf("requestId" to "nested"))) {
                        async { ThreadLogContext["requestId"] }.await() shouldBe "nested"
                        ThreadLogContext.containsKey("route") shouldBe false
                    }

                    ThreadLogContext["route"] shouldBe "/orders"
                    withContext(CoroutineThreadLogContext(includeCoroutineContext = false)) {
                        ThreadLogContext.toMap() shouldContainExactly mapOf("scope" to "outside")
                    }
                    ThreadLogContext["requestId"] shouldBe "request-1"
                }
            }

            ThreadLogContext.toMap() shouldContainExactly mapOf("scope" to "outside")
        }

        test("keeps writes and removals local to the installed thread context") {
            val request = CoroutineLogContext(mapOf("requestId" to "request-1", "route" to null))

            withContext(CoroutineThreadLogContext(includeCoroutineContext = true) + request) {
                ThreadLogContext.containsKey("route") shouldBe true
                ThreadLogContext.put("requestId", null) shouldBe "request-1"
                ThreadLogContext["requestId"] shouldBe null
                ThreadLogContext.putAll(mapOf("requestId" to "override"))
                ThreadLogContext["requestId"] shouldBe "override"
                ThreadLogContext.remove("requestId") shouldBe "override"
                ThreadLogContext.containsKey("requestId") shouldBe false

                withContext(Dispatchers.Default) {
                    ThreadLogContext.toMap() shouldContainExactly mapOf("route" to null)
                    ThreadLogContext.clear()
                }

                ThreadLogContext.isEmpty() shouldBe true
                async { ThreadLogContext.isEmpty() }.await() shouldBe true
                request.toMap() shouldContainExactly mapOf("requestId" to "request-1", "route" to null)
            }
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
