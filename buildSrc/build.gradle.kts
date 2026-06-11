plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(kt.kotlin.gradlePlugin)
    implementation(kt.dokka)
    implementation(kt.dokka.javadoc)
}
