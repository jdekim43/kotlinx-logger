package convention

plugins {
    kotlin("jvm")
    id("maven-publish")
}

kotlin {
    jvmToolchain(11)
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.getByName("main").allSource)
}

publishing {
    publications {
        create<MavenPublication>("lib") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
            from(components["java"])
            artifact(sourcesJar)
        }
    }
}