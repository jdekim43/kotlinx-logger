plugins {
    id("convention.kotlin-multiplatform")
    id("convention.publish")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-logger"))

            api(kt.ktor.server.core)
            api(kt.ktor.server.callId)
        }
    }
}