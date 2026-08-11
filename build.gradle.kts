import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension
import org.jreleaser.model.Active
import org.jreleaser.model.Signing

plugins {
    id("java")
    alias(libs.plugins.jreleaser)
}

val releaseVersion = providers.gradleProperty("releaseVersion").getOrElse("3.0.0-SNAPSHOT")
val nonJvmArtifactIds = listOf(
    "js",
    "macosarm64",
    "iosarm64",
    "iosx64",
    "iossimulatorarm64",
    "watchosarm64",
    "watchossimulatorarm64",
    "tvosarm64",
    "tvossimulatorarm64",
    "linuxx64",
    "linuxarm64",
    "mingwx64",
    "android"
)

allprojects {
    group = "kim.jade"
    version = releaseVersion
}

// Overrides for the Kotlin/JS toolchain npm dependencies in kotlin-js-store/yarn.lock.
// These are build-time only (bundler and test runner) and are not part of any published artifact,
// but they are still reported by Dependabot. Remove an entry once the Kotlin Gradle plugin
// ships a version at or above the patched one.
plugins.withType<YarnPlugin> {
    // GHSA-38r7-794h-5758 (< 5.104.0) and GHSA-8fgc-7cc6-rx7x (<= 5.104.0): allowedUris
    // allow-list bypass in the buildHttp HttpUriPlugin.
    the<NodeJsRootExtension>().versions.webpack.version = "5.104.1"

    // Both of these are transitive dependencies of mocha, and mocha still declares semver ranges
    // that can never resolve to the patched major (as of mocha 11.8.0, the latest release).
    // Overriding here is the only way to get the patched versions until mocha widens its ranges.
    the<YarnRootExtension>().apply {
        // GHSA-5c6j-r48x-rmvq (<= 7.0.2) and GHSA-qj8w-gfj5-8c6v (< 7.0.5); mocha requests ^6.0.2.
        resolution("serialize-javascript", "7.0.5")

        // GHSA-73rr-hh4g-fpgx (< 8.0.3); mocha requests ^7.0.0.
        resolution("diff", "8.0.3")
    }
}

jreleaser {
    project {
        author("Jade Kim")
        license.set("Apache-2.0")
        links {
            vcsBrowser.set("https://github.com/jdekim43/kotlinx-logger")
        }
        inceptionYear.set("2026")
    }

    signing {
        active.set(Active.ALWAYS)
        armored.set(true)
        mode.set(Signing.Mode.FILE)
    }

    deploy {
        maven {
            mavenCentral {
                create("release") {
                    active.set(Active.RELEASE)
                    url.set("https://central.sonatype.com/api/v1/publisher")
                    skipPublicationCheck.set(true)

                    subprojects.forEach {
                        stagingRepository(it.layout.buildDirectory.dir("staging-deploy").get().asFile.absolutePath)

                        nonJvmArtifactIds.forEach { target ->
                            artifactOverride {
                                artifactId = "${it.name}-$target"
                                jar = false
                                verifyPom = false
                                sourceJar = false
                                javadocJar = false
                            }
                        }
                    }
                }
            }
            nexus2 {
                create("snapshot") {
                    active.set(Active.SNAPSHOT)
                    url.set("https://central.sonatype.com/repository/maven-snapshots")
                    snapshotUrl.set("https://central.sonatype.com/repository/maven-snapshots")
                    applyMavenCentralRules.set(true)
                    snapshotSupported.set(true)
                    closeRepository.set(true)
                    releaseRepository.set(true)

                    subprojects.forEach {
                        stagingRepository(it.layout.buildDirectory.dir("staging-deploy").get().asFile.absolutePath)

                        nonJvmArtifactIds.forEach { target ->
                            artifactOverride {
                                artifactId = "${it.name}-$target"
                                jar = false
                                verifyPom = false
                                sourceJar = false
                                javadocJar = false
                            }
                        }
                    }
                }
            }
        }
    }

    release {
        github {
            repoOwner = "jdekim43"
            prerelease.pattern = ".*-*"
        }
    }
}

val clearStagingDirectory = tasks.create<Delete>("clearStagingDirectory") {
    delete(layout.buildDirectory.dir("staging-deploy"))

    subprojects.forEach {
        delete(it.layout.buildDirectory.dir("staging-deploy"))
    }
}

tasks.register("publish") {
    group = "publishing"

    subprojects.forEach {
        val publishTask = it.tasks.named("publish")
        publishTask.configure { dependsOn(clearStagingDirectory) }
        dependsOn(publishTask)
    }
}

val moduleJvmTests = listOf(
    ":kotlinx-logger:jvmTest",
    ":kotlinx-logger-integration-gson:test",
    ":kotlinx-logger-integration-jackson:test",
    ":kotlinx-logger-integration-jvm:test",
    ":kotlinx-logger-integration-koin:jvmTest",
    ":kotlinx-logger-integration-ktor:jvmTest",
    ":kotlinx-logger-integration-okhttp:test",
    ":kotlinx-logger-integration-opentelemetry:test",
    ":kotlinx-logger-integration-sentry:test",
)

val allJvmTests = tasks.register("allJvmTests") {
    group = "verification"
    description = "Runs the JVM Kotest suite for every library module."
    dependsOn(moduleJvmTests)
}

val commonTestModules = listOf(
    ":kotlinx-logger",
    ":kotlinx-logger-integration-koin",
)

val hostNativeTest = when {
    System.getProperty("os.name").startsWith("Mac") &&
        System.getProperty("os.arch") in setOf("aarch64", "arm64") -> "macosArm64Test"
    System.getProperty("os.name").startsWith("Linux") &&
        System.getProperty("os.arch") in setOf("amd64", "x86_64") -> "linuxX64Test"
    System.getProperty("os.name").startsWith("Windows") &&
        System.getProperty("os.arch") in setOf("amd64", "x86_64") -> "mingwX64Test"
    else -> null
}

val moduleMultiplatformTests = commonTestModules.flatMap { module ->
    buildList {
        add("$module:jsBrowserTest")
        add("$module:jsNodeTest")
        add("$module:testAndroidHostTest")
        hostNativeTest?.let { add("$module:$it") }
    }
}

val multiplatformTests = tasks.register("multiplatformTests") {
    group = "verification"
    description = "Runs JVM-only tests and common tests on JS, Android, and the current native host."
    dependsOn(allJvmTests)
    dependsOn(moduleMultiplatformTests)
}

tasks.named("check") {
    dependsOn(multiplatformTests)
}
