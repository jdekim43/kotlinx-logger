package kim.jade.kotlinx.logger

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import kim.jade.kotlinx.logger.pipeline.LogPipe
import kim.jade.kotlinx.logger.pipeline.LogPipeline

/** Unique per instance so that installing several capture pipes does not replace by key. */
private class HierarchyCaptureKey : LogPipe.Key<HierarchyCapturePipe>

private class HierarchyCapturePipe : LogPipe {
    val records = mutableListOf<LogRecord>()

    override val key: LogPipe.Key<out LogPipe> = HierarchyCaptureKey()

    override fun apply(record: LogRecord, next: (LogRecord) -> Unit) {
        records += record
        next(record)
    }
}

/**
 * Unlike [LoggerTest], these cannot inject configuration through the constructor — the hierarchy is resolved
 * through the registry. Every test uses its own name prefix and the defaults are restored afterwards.
 * `kim` and `kim.jade` are deliberately left alone: [LoggerTest] registers a logger under that package.
 */
class LoggerHierarchyTest : FunSpec({

    lateinit var originalLevel: LogLevel
    lateinit var originalPipeline: LogPipeline

    beforeTest {
        originalLevel = Logger.defaultLevel
        originalPipeline = Logger.defaultPipeline
    }

    afterTest {
        Logger.resetAllConfiguration()
        Logger.defaultPipeline = originalPipeline
        Logger.defaultLevel = originalLevel
    }

    context("root") {
        test("the companion initializes without re-entering itself") {
            shouldNotThrowAny {
                Logger.defaultPipeline
                Logger.root
            }
        }

        test("root is the logger registered under the empty name") {
            Logger.named(Logger.ROOT_LOGGER_NAME) shouldBeSameInstanceAs Logger.root
            Logger.root.name shouldBe Logger.ROOT_LOGGER_NAME
            Logger.named(Logger.ROOT_LOGGER_NAME).parent.shouldBeNull()
        }

        test("root falls back to the defaults without copying the pipeline") {
            Logger.defaultLevel = LogLevel.WARNING

            Logger.root.level shouldBe LogLevel.WARNING
            Logger.root.pipeline shouldBeSameInstanceAs Logger.defaultPipeline
        }
    }

    context("inheritance") {
        test("a logger without configuration inherits the default level") {
            Logger.defaultLevel = LogLevel.ERROR

            Logger.named("hier1.a.b").level shouldBe LogLevel.ERROR
        }

        test("configuration comes from the nearest registered ancestor") {
            Logger.configure("hier2.a") { level = LogLevel.WARNING }
            Logger.configure("hier2.a.b") { level = LogLevel.TRACE }

            Logger.named("hier2.a.b.c").level shouldBe LogLevel.TRACE
            Logger.named("hier2.a.x").level shouldBe LogLevel.WARNING
        }

        test("level and pipeline resolve independently") {
            val inherited = LogPipeline()
            Logger.configure("hier3.a") { pipeline = inherited }
            Logger.configure("hier3.a.b") { level = LogLevel.TRACE }

            Logger.named("hier3.a.b.c").run {
                level shouldBe LogLevel.TRACE
                pipeline shouldBeSameInstanceAs inherited
            }
        }

        test("useParentLevel restores inheritance") {
            Logger.configure("hier4.a") { level = LogLevel.WARNING }
            val child = Logger.named("hier4.a.b")

            child.level = LogLevel.TRACE
            child.level shouldBe LogLevel.TRACE

            child.useParentLevel()
            child.level shouldBe LogLevel.WARNING
        }

        test("useParentPipeline restores inheritance and drops the from-parent block") {
            val ancestorPipeline = LogPipeline()
            Logger.configure("hier4b.a") { pipeline = ancestorPipeline }
            val child = Logger.configure("hier4b.a.b") {
                pipeline = LogPipeline()
                configurePipelineFromParent { install(HierarchyCapturePipe()) }
            }

            child.pipeline shouldNotBeSameInstanceAs ancestorPipeline

            child.useParentPipeline()
            child.pipeline shouldBeSameInstanceAs ancestorPipeline
        }

        test("an explicit pipeline stops inheritance") {
            val ancestorPipe = HierarchyCapturePipe()
            val ownPipe = HierarchyCapturePipe()
            Logger.defaultLevel = LogLevel.TRACE
            Logger.configure("hier5.a") { pipeline = LogPipeline().install(ancestorPipe) }
            val child = Logger.configure("hier5.a.b") { pipeline = LogPipeline().install(ownPipe) }

            child.info("message")

            ownPipe.records shouldHaveSize 1
            ancestorPipe.records shouldHaveSize 0
        }

        test("a record logged by a descendant reaches the ancestor pipeline under its own name") {
            val capture = HierarchyCapturePipe()
            Logger.configure("hier6.a") {
                level = LogLevel.TRACE
                pipeline = LogPipeline().install(capture)
            }

            Logger.named("hier6.a.b.c").debug("through the ancestor")

            capture.records.single().run {
                loggerName shouldBe "hier6.a.b.c"
                level shouldBe LogLevel.DEBUG
            }
        }
    }

    context("invalidation") {
        test("configuring an ancestor invalidates a descendant that already resolved") {
            val child = Logger.named("hier7.a.b")
            child.level shouldBe Logger.defaultLevel

            Logger.configure("hier7.a") { level = LogLevel.TRACE }

            child.level shouldBe LogLevel.TRACE
        }

        test("an ancestor registered later takes over once it is configured") {
            Logger.configure("hier8.a") { level = LogLevel.WARNING }
            val child = Logger.named("hier8.a.b.c")
            child.level shouldBe LogLevel.WARNING

            Logger.configure("hier8.a.b") { level = LogLevel.TRACE }

            child.level shouldBe LogLevel.TRACE
            child.parent shouldBeSameInstanceAs Logger.named("hier8.a.b")
        }

        test("mutating an inherited pipeline in place reaches descendants that already resolved") {
            val capture = HierarchyCapturePipe()
            Logger.defaultLevel = LogLevel.TRACE
            Logger.defaultPipeline = LogPipeline()

            val child = Logger.named("hier9.a.b")
            child.pipeline shouldBeSameInstanceAs Logger.defaultPipeline

            Logger.defaultPipeline.install(capture)
            child.info("after install")

            capture.records shouldHaveSize 1
        }

        test("replacing an inherited pipeline with a look-alike instance is not mistaken for the old one") {
            val first = HierarchyCapturePipe()
            val second = HierarchyCapturePipe()
            Logger.defaultLevel = LogLevel.TRACE
            Logger.defaultPipeline = LogPipeline().install(first)

            val child = Logger.named("hier10.a.b")
            child.info("first")

            Logger.defaultPipeline = LogPipeline().install(second)
            child.info("second")

            first.records shouldHaveSize 1
            second.records shouldHaveSize 1
        }

        test("a logger with its own level still follows ancestor pipeline changes") {
            val capture = HierarchyCapturePipe()
            Logger.defaultPipeline = LogPipeline()
            val child = Logger.configure("hier11.a.b") { level = LogLevel.TRACE }
            child.info("primes the cache")

            Logger.defaultPipeline = LogPipeline().install(capture)
            child.info("after replacement")

            capture.records shouldHaveSize 1
        }

        test("logging repeatedly neither re-resolves nor invalidates") {
            Logger.defaultLevel = LogLevel.TRACE
            Logger.defaultPipeline = LogPipeline()
            val child = Logger.configure("hier12.a.b") {
                configurePipelineFromParent { install(HierarchyCapturePipe()) }
            }

            val resolved = child.pipeline
            val generation = Logger.ConfigurationSnapshot.current()
            repeat(5) { child.info("message $it") }

            child.pipeline shouldBeSameInstanceAs resolved
            Logger.ConfigurationSnapshot.current() shouldBe generation
        }
    }

    context("configurePipelineFromParent") {
        test("wraps the inherited pipeline without touching the ancestor") {
            val inheritedPipe = HierarchyCapturePipe()
            val ownPipe = HierarchyCapturePipe()
            Logger.defaultLevel = LogLevel.TRACE
            Logger.defaultPipeline = LogPipeline().install(inheritedPipe)

            val child = Logger.configure("hier13.a.b") {
                configurePipelineFromParent { install(ownPipe) }
            }
            child.info("from the child")
            Logger.named("hier13.sibling").info("from the sibling")

            inheritedPipe.records shouldHaveSize 2
            ownPipe.records shouldHaveSize 1
            child.pipeline shouldNotBeSameInstanceAs Logger.defaultPipeline
        }

        test("is re-applied after the inherited pipeline changes") {
            val ownPipe = HierarchyCapturePipe()
            Logger.defaultLevel = LogLevel.TRACE
            Logger.defaultPipeline = LogPipeline()
            val child = Logger.configure("hier14.a.b") {
                configurePipelineFromParent { install(ownPipe) }
            }
            child.info("first")

            Logger.defaultPipeline = LogPipeline().install(HierarchyCapturePipe())
            child.info("second")

            ownPipe.records shouldHaveSize 2
        }
    }

    context("explicit parent") {
        test("overrides the name hierarchy and can be undone") {
            Logger.configure("hier15.a") { level = LogLevel.WARNING }
            val donor = Logger.configure("hier15.donor") { level = LogLevel.TRACE }
            val child = Logger.named("hier15.a.b")

            child.level shouldBe LogLevel.WARNING

            child.parent = donor
            child.level shouldBe LogLevel.TRACE
            child.parent shouldBeSameInstanceAs donor

            child.resetParent()
            child.level shouldBe LogLevel.WARNING
        }

        test("still caches its resolution") {
            Logger.defaultPipeline = LogPipeline()
            val donor = Logger.configure("hier16.donor") {
                level = LogLevel.TRACE
                pipeline = LogPipeline()
            }
            val child = Logger.named("hier16-unrelated").apply { parent = donor }

            val resolved = child.pipeline
            child.level shouldBe LogLevel.TRACE
            child.pipeline shouldBeSameInstanceAs resolved
            child.parent shouldBeSameInstanceAs donor
        }

        test("rejects a cycle") {
            val ancestor = Logger.named("hier17.a")
            val descendant = Logger.named("hier17.a.b")
            descendant.parent = ancestor

            shouldThrow<IllegalArgumentException> { ancestor.parent = descendant }
            shouldThrow<IllegalArgumentException> { ancestor.parent = ancestor }
        }
    }

    context("name edge cases") {
        test("a name without a separator is a child of root") {
            Logger.defaultLevel = LogLevel.ERROR

            Logger.named("noseparator").run {
                parent shouldBeSameInstanceAs Logger.root
                level shouldBe LogLevel.ERROR
            }
        }

        test("a leading separator resolves to root") {
            Logger.named(".leading").parent shouldBeSameInstanceAs Logger.root
        }

        test("a trailing separator still resolves the preceding segment") {
            val ancestor = Logger.configure("trailing") { level = LogLevel.TRACE }

            Logger.named("trailing.").parent shouldBeSameInstanceAs ancestor
        }

        test("consecutive separators skip the empty segment") {
            val ancestor = Logger.configure("double") { level = LogLevel.TRACE }

            Logger.named("double..dot").parent shouldBeSameInstanceAs ancestor
        }
    }

    context("registry") {
        test("directly constructed loggers inherit but never become ancestors") {
            Logger.defaultLevel = LogLevel.INFO
            Logger.configure("hier18.a") { level = LogLevel.WARNING }

            Logger("hier18.a.b").level shouldBe LogLevel.WARNING

            Logger("hier18.c", LogLevel.TRACE)
            Logger.named("hier18.c.d").level shouldBe LogLevel.INFO
        }

        test("configure registers the logger and returns the shared instance") {
            val configured = Logger.configure("hier19.a") { level = LogLevel.TRACE }

            configured shouldBeSameInstanceAs Logger.named("hier19.a")
            Logger.named("hier19.a").level shouldBe LogLevel.TRACE
        }

        test("resetAllConfiguration drops every explicit configuration") {
            Logger.defaultLevel = LogLevel.INFO
            Logger.configure("hier20.a") { level = LogLevel.TRACE }
            Logger.named("hier20.a.b").level shouldBe LogLevel.TRACE

            Logger.resetAllConfiguration()

            Logger.named("hier20.a").level shouldBe LogLevel.INFO
            Logger.named("hier20.a.b").level shouldBe LogLevel.INFO
        }
    }
})
