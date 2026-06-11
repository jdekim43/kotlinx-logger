plugins {
    id("convention.kotlin-jvm")
    id("convention.publish")
}

dependencies {
    implementation(kotlin("reflect"))

    implementation(project(":"))

    implementation(libs.gson)
}
