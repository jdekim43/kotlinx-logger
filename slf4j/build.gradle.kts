plugins {
    id("convention.kotlin-jvm")
    id("convention.publish")
}

dependencies {
    implementation(project(":"))

    implementation(libs.slf4j.api)
}
