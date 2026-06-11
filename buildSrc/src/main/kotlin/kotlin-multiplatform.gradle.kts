package convention

plugins {
    kotlin("multiplatform")
}

kotlin {
    jvmToolchain(8)

    jvm()

    js {
        browser()
        nodejs()
    }

    iosArm64()
    iosSimulatorArm64()
}
