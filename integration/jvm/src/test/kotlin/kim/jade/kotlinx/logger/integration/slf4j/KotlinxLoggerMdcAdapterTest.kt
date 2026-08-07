package kim.jade.kotlinx.logger.integration.slf4j

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kim.jade.kotlinx.logger.context.ThreadLogContext
import org.slf4j.helpers.BasicMarkerFactory
import org.slf4j.spi.MDCAdapter
import org.slf4j.spi.SLF4JServiceProvider
import java.util.ServiceLoader

class KotlinxLoggerMdcAdapterTest : FunSpec({

    val mdc = KotlinxLoggerMdcAdapter()

    fun clearThreadState() {
        ThreadLogContext.clear()
        ThreadLogContext.clearStack("request")
        ThreadLogContext.clearStack("user")
    }

    beforeTest { clearThreadState() }
    afterTest { clearThreadState() }

    context("map operations") {
        test("MDC operations are backed by the current thread log context") {
            mdc.put("requestId", "request-1")

            mdc.get("requestId") shouldBe "request-1"
            ThreadLogContext["requestId"] shouldBe "request-1"
            mdc.copyOfContextMap.shouldContainExactly(mapOf("requestId" to "request-1"))

            val copy = mdc.copyOfContextMap
            copy["requestId"] = "changed"
            mdc.get("requestId") shouldBe "request-1"

            mdc.remove("requestId")
            mdc.get("requestId") shouldBe null
            mdc.clear()
            ThreadLogContext.isEmpty() shouldBe true
        }

        test("setContextMap replaces the previous context and snapshots expose SLF4J values") {
            mdc.put("stale", "remove-me")
            ThreadLogContext["staleNumber"] = 1

            mdc.setContextMap(mutableMapOf("text" to "value", "empty" to null))
            ThreadLogContext["number"] = 3

            mdc.copyOfContextMap.shouldContainExactly(mapOf("text" to "value", "empty" to null))
            mdc.get("number") shouldBe null
            ThreadLogContext.containsKey("stale") shouldBe false
            ThreadLogContext.containsKey("staleNumber") shouldBe false

            mdc.setContextMap(null)
            mdc.copyOfContextMap.isEmpty() shouldBe true
        }
    }

    context("deque operations") {
        test("deque operations preserve LIFO order and isolate stacks by key") {
            mdc.put("request", "mapped")

            mdc.pushByKey("request", "first")
            mdc.pushByKey("request", "second")
            mdc.pushByKey("user", "user-7")

            mdc.get("request") shouldBe "mapped"
            val requestCopy = requireNotNull(mdc.getCopyOfDequeByKey("request"))
            requestCopy.shouldContainExactly("second", "first")
            requireNotNull(mdc.getCopyOfDequeByKey("user")).shouldContainExactly("user-7")

            requestCopy.pop() shouldBe "second"
            requireNotNull(mdc.getCopyOfDequeByKey("request")).shouldContainExactly("second", "first")

            mdc.popByKey("request") shouldBe "second"
            mdc.popByKey("request") shouldBe "first"
            mdc.popByKey("request") shouldBe null
            mdc.getCopyOfDequeByKey("request") shouldBe null
            mdc.get("request") shouldBe "mapped"
            mdc.popByKey("user") shouldBe "user-7"
            mdc.popByKey("user") shouldBe null
        }

        test("clearDequeByKey clears only the selected stack") {
            mdc.put("request", "mapped")
            mdc.pushByKey("request", "request-1")
            mdc.pushByKey("user", "user-1")

            mdc.clearDequeByKey("request")

            mdc.getCopyOfDequeByKey("request") shouldBe null
            requireNotNull(mdc.getCopyOfDequeByKey("user")).shouldContainExactly("user-1")
            mdc.get("request") shouldBe "mapped"
        }

        test("map operations do not modify deque state") {
            mdc.pushByKey("request", "first")
            mdc.pushByKey("request", "second")

            mdc.put("request", "mapped")
            mdc.remove("request")
            mdc.setContextMap(mutableMapOf("replacement" to "value"))
            mdc.clear()

            requireNotNull(mdc.getCopyOfDequeByKey("request")).shouldContainExactly("second", "first")
        }

        test("deque rejects null values without changing existing stack") {
            val api: MDCAdapter = mdc
            mdc.pushByKey("request", "first")

            shouldThrow<NullPointerException> {
                api.pushByKey("request", null)
            }

            requireNotNull(mdc.getCopyOfDequeByKey("request")).shouldContainExactly("first")
        }
    }

    context("service provider") {
        test("SLF4J service provider initializes all bridge components") {
            val provider = KotlinxLoggerServiceProvider()

            provider.initialize()

            provider.loggerFactory.shouldBeInstanceOf<KotlinxLoggerFactory>()
            provider.markerFactory.shouldBeInstanceOf<BasicMarkerFactory>()
            provider.mdcAdapter.shouldBeInstanceOf<KotlinxLoggerMdcAdapter>()
            provider.requestedApiVersion shouldBe KotlinxLoggerServiceProvider.REQUESTED_API_VERSION
        }

        test("SLF4J service provider is discoverable through ServiceLoader") {
            val providers = ServiceLoader.load(SLF4JServiceProvider::class.java).toList()

            providers.any { it is KotlinxLoggerServiceProvider } shouldBe true
        }
    }
})
