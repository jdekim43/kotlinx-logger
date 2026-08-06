plugins {
    id("convention.kotlin-jvm")
    id("convention.publish")
}

dependencies {
    api(project(":kotlinx-logger"))

    api(libs.otel.api)

    implementation(kotlin("reflect"))
}