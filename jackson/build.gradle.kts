plugins {
    id("convention.kotlin-jvm")
    id("convention.publish")
}

dependencies {
    implementation(project(":"))

    implementation(libs.jackson.kotlin) {
        exclude("com.fasterxml.jackson.core", "jackson-databind")
    }
    implementation(libs.jackson.databind)
}
