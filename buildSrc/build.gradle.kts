plugins {
    `kotlin-dsl`
}

repositories {
    google()
    gradlePluginPortal()
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation(kt.kotlin.gradlePlugin)
    implementation(libs.android.kmp.library.gradlePlugin)
    implementation(kt.dokka)
    implementation(kt.dokka.javadoc)
}
