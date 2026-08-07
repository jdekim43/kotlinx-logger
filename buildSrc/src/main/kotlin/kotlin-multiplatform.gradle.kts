package convention

import gradle.kotlin.dsl.accessors._0b22db41394044cafe0429d1aeba93fd.testImplementation
import gradle.kotlin.dsl.accessors._0b22db41394044cafe0429d1aeba93fd.testRuntimeOnly
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("com.google.devtools.ksp")
    id("io.kotest")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
kotlin {
    jvmToolchain(11)

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    //web
    js {
        browser()
        nodejs()

        compilerOptions {
            target = "es2015"
        }
    }

    //apple
    macosArm64()

    iosArm64()
    iosX64()
    iosSimulatorArm64()

    watchosArm64()
    watchosSimulatorArm64()

    tvosArm64()
    tvosSimulatorArm64()

    //desktop
    linuxX64()
    linuxArm64()
    mingwX64()

    //android
    android {
        namespace = "${project.group}.${project.name.replace("-", ".")}"

        compileSdk = 37
        minSdk = 21

        withJava()
        withHostTest {}

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(libs.findLibrary("kotest-framework-engine").get())
            implementation(libs.findLibrary("kotest-assertions-core").get())
        }

        jvmTest.dependencies {
            runtimeOnly(libs.findLibrary("kotest-runner-junit5").get())
        }

        named("androidHostTest").dependencies {
            runtimeOnly(libs.findLibrary("kotest-runner-junit5").get())
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
