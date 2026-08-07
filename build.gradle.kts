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
            skipTag = true
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
