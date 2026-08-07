plugins {
    id("convention.kotlin-jvm")
    id("convention.publish")
}

dependencies {
    implementation(kotlin("reflect"))

    api(project(":kotlinx-logger"))

    api(libs.gson)
}
