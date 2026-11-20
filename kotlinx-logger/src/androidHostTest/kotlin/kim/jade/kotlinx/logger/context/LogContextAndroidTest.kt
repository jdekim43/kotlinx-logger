package kim.jade.kotlinx.logger.context

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class LogContextAndroidTest : FunSpec({
    test("withLogContext preserves thread writes across dispatcher switches and restores the outer scope") {
        val outer = CoroutineLogContext(mapOf("requestId" to "outer"))
        val inner = CoroutineLogContext(mapOf("requestId" to "inner"))

        withLogContext(outer) {
            ThreadLogContext["requestId"] shouldBe "outer"
            ThreadLogContext["operation"] = "outer"

            withLogContext(inner) {
                ThreadLogContext["requestId"] shouldBe "inner"
                ThreadLogContext["operation"] = "inner"
                ThreadLogContext.remove("requestId")

                Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
                    withContext(dispatcher) {
                        ThreadLogContext["requestId"] shouldBe null
                        ThreadLogContext["operation"] shouldBe "inner"
                    }
                }
            }

            ThreadLogContext["operation"] shouldBe "outer"
            ThreadLogContext["requestId"] shouldBe "outer"
        }
    }
})
