plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish)
}

dependencies {
    // Detekt API - compileOnly to avoid runtime conflicts
    compileOnly(libs.detekt.api)
    
    // Testing
    testImplementation(libs.detekt.api)
    testImplementation(libs.detekt.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
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
