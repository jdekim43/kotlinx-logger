package convention

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

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
    macosArm64()

    //android
    android {
        namespace = "${project.group}.${project.name.replace("-", ".")}"

        compileSdk = 37
        minSdk = 21

        withJava()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
}
