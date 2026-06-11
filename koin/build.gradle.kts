plugins {
    id("convention.kotlin-multiplatform")
    id("convention.publish")
}

kotlin {

    sourceSets {
        commonMain.dependencies {
            implementation(project(":"))

            implementation(libs.koin.core)
        }
    }
}
