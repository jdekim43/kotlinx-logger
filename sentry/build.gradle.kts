plugins {
    id("convention.kotlin-jvm")
    id("convention.publish")
}

dependencies {
    implementation(project(":"))

    implementation(libs.sentry)
    implementation(libs.sentry.kotlin)
}
