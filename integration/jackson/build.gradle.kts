plugins {
    id("convention.kotlin-jvm")
    id("convention.publish")
}

dependencies {
    implementation(kotlin("reflect"))

    api(project(":kotlinx-logger"))

    api(libs.jackson.databind)
    api(libs.jackson.kotlin)
}

tasks.withType<Test>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    })
}
