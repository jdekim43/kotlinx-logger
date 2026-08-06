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
