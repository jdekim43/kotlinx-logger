package kim.jade.kotlinx.logger.context

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import kotlinx.coroutines.withContext

class LogContextTest : FunSpec({

    context("immutable LogContext") {
        test("copies its input so later source mutations are invisible") {
            val source = mutableMapOf<String, Any?>("tenant" to "alpha", "region" to "kr")
            val context = LogContext(source)

            source["tenant"] = "changed"

            context shouldContainExactly mapOf("tenant" to "alpha", "region" to "kr")
        }

        test("merges with right-hand values taking precedence") {
            val context = LogContext(mapOf("tenant" to "alpha", "region" to "kr"))

            val merged = context + mapOf("region" to "us", "traceId" to "trace-1")

            merged shouldContainExactly mapOf(
                "tenant" to "alpha",
                "region" to "us",
                "traceId" to "trace-1",
            )
        }

        test("merging null returns the same instance") {
            val context = LogContext(mapOf("tenant" to "alpha"))

            (context + null) shouldBeSameInstanceAs context
        }
    }

    context("MutableLogContext") {
        test("clones and snapshots do not share subsequent mutations") {
            val context = MutableLogContext(mapOf("requestId" to "request-1"))
            val clone = context.clone()
            val snapshot = context.toImmutable()
            val merged = context + mapOf("attempt" to 2)

            context["requestId"] = "request-2"
            clone["cloneOnly"] = true
            merged["mergedOnly"] = true

            clone shouldContainExactly mapOf("requestId" to "request-1", "cloneOnly" to true)
            snapshot shouldContainExactly mapOf("requestId" to "request-1")
            merged shouldContainExactly mapOf(
                "requestId" to "request-1",
                "attempt" to 2,
                "mergedOnly" to true,
            )
            context shouldContainExactly mapOf("requestId" to "request-2")
        }

        test("merging null copies instead of returning the same mutable instance") {
            val context = MutableLogContext(mapOf("requestId" to "request-1"))

            (context + null) shouldNotBeSameInstanceAs context
            (context + null) shouldBe context
        }

        test("plusAssign merges a context and safely ignores null") {
            val context = MutableLogContext(mapOf("one" to 1))

            context += LogContext(mapOf("two" to 2))
            context += null

            context shouldContainExactly mapOf("one" to 1, "two" to 2)
        }
    }

    context("global and thread contexts") {
        lateinit var savedGlobal: Map<String, Any?>
        lateinit var savedThread: Map<String, Any?>

        beforeTest {
            savedGlobal = GlobalLogContext.toMap()
            savedThread = ThreadLogContext.toMap()
            GlobalLogContext.clear()
            ThreadLogContext.clear()
        }

        afterTest {
            GlobalLogContext.clear()
            GlobalLogContext.putAll(savedGlobal)
            ThreadLogContext.clear()
            ThreadLogContext.putAll(savedThread)
        }

        test("current snapshots combine global and thread values and remain immutable snapshots") {
            GlobalLogContext.putAll(mapOf("service" to "checkout", "scope" to "global"))
            ThreadLogContext.putAll(mapOf("requestId" to "request-7", "scope" to "thread"))

            val snapshot = snapCurrentLogContext()

            GlobalLogContext["service"] = "changed"
            ThreadLogContext["requestId"] = "changed"
            snapshot shouldContainExactly mapOf(
                "service" to "checkout",
                "scope" to "thread",
                "requestId" to "request-7",
            )
        }
    }

    context("thread stacks") {
        val key = "thread-stack-test"
        var hadMapValue = false
        var savedMapValue: Any? = null
        var savedStack: List<Any?>? = null

        beforeTest {
            hadMapValue = ThreadLogContext.containsKey(key)
            savedMapValue = ThreadLogContext[key]
            savedStack = ThreadLogContext.copyAllInStack(key)
            ThreadLogContext.clearStack(key)
        }

        afterTest {
            ThreadLogContext.clearStack(key)
            savedStack?.forEach { value -> value?.let { ThreadLogContext.push(key, it) } }
            if (hadMapValue) {
                ThreadLogContext[key] = savedMapValue
            } else {
                ThreadLogContext.remove(key)
            }
        }

        test("stacks are LIFO snapshots independent from thread context map values") {
            ThreadLogContext[key] = "mapped"

            ThreadLogContext.push(key, "first")
            ThreadLogContext.push(key, "second")

            ThreadLogContext[key] shouldBe "mapped"
            val snapshot = requireNotNull(ThreadLogContext.copyAllInStack(key))
            snapshot.shouldContainExactly("first", "second")

            ThreadLogContext.push(key, "third")
            snapshot.shouldContainExactly("first", "second")
            ThreadLogContext.pop(key) shouldBe "third"
            ThreadLogContext.pop(key) shouldBe "second"
            ThreadLogContext.pop(key) shouldBe "first"
            ThreadLogContext.pop(key) shouldBe null
            ThreadLogContext.copyAllInStack(key) shouldBe null
            ThreadLogContext[key] shouldBe "mapped"
        }
    }

    context("CoroutineLogContext") {
        test("contexts are retrieved from an explicit or current coroutine context") {
            val installed = CoroutineLogContext(mapOf("jobId" to "job-9"))

            CoroutineLogContext.get(installed) shouldBeSameInstanceAs installed

            withContext(installed) {
                val current = CoroutineLogContext.get()

                current shouldBeSameInstanceAs installed
                current["jobId"] shouldBe "job-9"
            }
        }

        test("getting a missing coroutine context returns a fresh empty context") {
            val first = CoroutineLogContext.get()
            val second = CoroutineLogContext.get()

            first shouldNotBeSameInstanceAs second
            first.isEmpty() shouldBe true
            second.isEmpty() shouldBe true
        }
    }

    context("scoped thread context") {
        afterTest {
            ThreadLogContext.reset()
        }

        test("entries added for a block do not outlive it") {
            ThreadLogContext["tenant"] = "alpha"

            withThreadLogContext("userId" to "user-1") {
                ThreadLogContext["userId"] shouldBe "user-1"
                ThreadLogContext["tenant"] shouldBe "alpha"
            }

            ThreadLogContext.containsKey("userId") shouldBe false
            ThreadLogContext["tenant"] shouldBe "alpha"
        }

        test("the previous state is restored even when the block fails") {
            ThreadLogContext["tenant"] = "alpha"

            shouldThrow<IllegalStateException> {
                withThreadLogContext("userId" to "user-1") { error("handler failed") }
            }

            ThreadLogContext.containsKey("userId") shouldBe false
            ThreadLogContext["tenant"] shouldBe "alpha"
        }

        test("writes made inside the block are rolled back too, so a pooled thread starts clean") {
            withThreadLogContext(mapOf("userId" to "user-1")) {
                ThreadLogContext["orderId"] = "order-2"
                ThreadLogContext.push("step", "validate")
            }

            ThreadLogContext.isEmpty() shouldBe true
            ThreadLogContext.copyAllInStack("step").shouldBeNull()
        }
    }
})
