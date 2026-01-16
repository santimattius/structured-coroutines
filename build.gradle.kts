plugins {
    kotlin("jvm") version "2.3.0" apply false
}

allprojects {
    group = "io.github.santimattius"
    version = "0.1.0"
    
    repositories {
        mavenCentral()
        google()
    }
}
