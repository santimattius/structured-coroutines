plugins {
    kotlin("jvm") version "2.3.0"
    `maven-publish`
}

dependencies {
    // Detekt API - compileOnly to avoid runtime conflicts
    compileOnly("io.gitlab.arturbosch.detekt:detekt-api:1.23.7")
    
    // Testing
    testImplementation("io.gitlab.arturbosch.detekt:detekt-api:1.23.7")
    testImplementation("io.gitlab.arturbosch.detekt:detekt-test:1.23.7")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.25.3")
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "structured-coroutines-detekt-rules"
            
            pom {
                name.set("Structured Coroutines Detekt Rules")
                description.set("Detekt rules for enforcing structured concurrency best practices in Kotlin Coroutines")
                url.set("https://github.com/santimattius/structured-coroutines")
                
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                
                developers {
                    developer {
                        id.set("santimattius")
                        name.set("Santiago Mattiauda")
                    }
                }
            }
        }
    }
}
