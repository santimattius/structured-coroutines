plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":annotations"))
    implementation(libs.kotlinx.coroutines.core)
}

kotlin {
    jvmToolchain(17)

    compilerOptions {
        // Enable the structured coroutines compiler plugin
        freeCompilerArgs.addAll(
            "-Xplugin=${project(":compiler").layout.buildDirectory.get().asFile}/libs/compiler-${project.version}.jar"
        )
    }
}
