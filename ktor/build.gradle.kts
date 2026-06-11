plugins {
    id("convention.kotlin-multiplatform")
    id("convention.publish")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":"))
            implementation(project(":${rootProject.name}-coroutine"))

            implementation(kt.ktor.server.core)
            implementation(kt.ktor.server.callId)
        }
    }
}
