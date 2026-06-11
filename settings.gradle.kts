rootProject.name = "j-logger"

fun use(name: String) {
    include(name)
    project(":$name").name = "${rootProject.name}-$name"
}

use("coroutine")
use("gson")
use("jackson")
use("koin")
use("ktor")
use("okhttp")
use("sentry")
use("slf4j")

dependencyResolutionManagement {
    // Use Maven Central and the Gradle Plugin Portal for resolving dependencies in the shared build logic (`buildSrc`) project.
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
        mavenLocal()
    }

    versionCatalogs {
        create("kt") {
            from(files("gradle/kotlin.versions.toml"))
        }
//        create("jade") {
//            from(files("gradle/jade.versions.toml"))
//        }
    }
}