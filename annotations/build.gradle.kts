plugins {
    kotlin("jvm")
    `maven-publish`
}

// No dependencies - annotations module is self-contained

kotlin {
    jvmToolchain(17)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "io.github.santimattius"
            artifactId = "structured-coroutines-annotations"
            version = "0.1.0"
            
            from(components["java"])
        }
    }
}
