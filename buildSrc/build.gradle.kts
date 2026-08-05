plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(kt.kotlin.gradlePlugin)
    implementation(kt.dokka)
    implementation(kt.dokka.javadoc)
}
