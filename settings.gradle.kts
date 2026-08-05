import kim.jade.gradle.plugin.cleanarch.plugin.module

rootProject.name = "kotlinx-logger"

pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}

plugins {
    id("kim.jade.gradle.plugin.cleanarch") version "0.1.18"
}

include(":kotlinx-logger")
module("integration-gson")
module("integration-jackson")
module("integration-jvm")
module("integration-koin")
module("integration-ktor")
module("integration-okhttp")
module("integration-opentelemetry")
module("integration-sentry")

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
        gradlePluginPortal()
    }

    versionCatalogs {
        create("kotlinWrappers") {
            from("org.jetbrains.kotlin-wrappers:kotlin-wrappers-catalog:2025.11.12")
        }

        create("kt") {
            from(files("gradle/kotlin.versions.toml"))
        }

        create("kotlincrypto") {
            from("org.kotlincrypto:version-catalog:0.8.0")
        }
    }
}