import org.jreleaser.model.Active
import org.jreleaser.model.Signing

plugins {
    id("convention.kotlin-multiplatform")
    id("convention.publish")

    alias(libs.plugins.jreleaser)
}

allprojects {
    group = "kr.jadekim"
    version = "2.2.0-beta1"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.stately.concurrency)
            implementation(libs.stately.collections)
        }
    }
}

jreleaser {
    project {
        author("Jade Kim")
        license.set("Apache-2.0")
        links {
            vcsBrowser.set("https://github.com/jdekim43/j-logger")
        }
        inceptionYear.set("2021")
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

                    listOf(rootProject).forEach {
                        stagingRepository(it.layout.buildDirectory.dir("staging-deploy").get().asFile.absolutePath)

                        listOf("iosarm64", "iossimulatorarm64", "js").forEach { target ->
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

                    listOf(rootProject).forEach {
                        stagingRepository(it.layout.buildDirectory.dir("staging-deploy").get().asFile.absolutePath)

                        listOf("iosarm64", "iossimulatorarm64", "js").forEach { target ->
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
        }
    }
}

val clearStagingDirectory = tasks.create<Delete>("clearStagingDirectory") {
    delete(layout.buildDirectory.dir("staging-deploy"))

    subprojects.forEach {
        delete(it.layout.buildDirectory.dir("staging-deploy"))
    }
}


tasks.named("publish") {
    dependsOn(clearStagingDirectory)
    subprojects.forEach {
        val publishTask = it.tasks.named("publish")
        publishTask.configure { dependsOn(clearStagingDirectory) }
        dependsOn(publishTask)
    }

    finalizedBy(":jreleaserFullRelease")
}
