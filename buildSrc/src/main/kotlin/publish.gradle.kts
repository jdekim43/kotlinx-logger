package convention

import org.jetbrains.dokka.gradle.tasks.DokkaGeneratePublicationTask

plugins {
    id("maven-publish")
    id("org.jetbrains.dokka")
    id("org.jetbrains.dokka-javadoc")
}

val javadocJar: TaskProvider<Jar> by tasks.registering(Jar::class) {
    dependsOn(tasks.named<DokkaGeneratePublicationTask>("dokkaGeneratePublicationHtml"))
    archiveClassifier.set("javadoc")
    from(tasks.named<DokkaGeneratePublicationTask>("dokkaGeneratePublicationHtml").flatMap { it.outputDirectory })
}

configure<PublishingExtension> {
    repositories {
        maven {
            setUrl(layout.buildDirectory.dir("staging-deploy"))
        }
    }

    publications.withType<MavenPublication> {
        artifact(javadocJar)
        pom {
            name.set(project.name)
            description.set("Kotlin Multiplatform Utilities")
            url.set("https://github.com/jdekim43/kotlinx")
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    id.set("jdekim43")
                    name.set("Jade Kim")
                }
            }
            scm {
                connection.set("scm:git:https://github.com/jdekim43/kotlinx.git")
                developerConnection.set("scm:git:ssh://git@github.com/jdekim43/kotlinx.git")
                url.set("https://github.com/jdekim43/kotlinx")
            }
        }
    }
}
