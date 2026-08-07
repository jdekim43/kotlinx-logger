plugins {
    id("convention.kotlin-jvm")
    id("convention.publish")
}

dependencies {
    api(project(":kotlinx-logger"))

    implementation(libs.slf4j.api)
}
