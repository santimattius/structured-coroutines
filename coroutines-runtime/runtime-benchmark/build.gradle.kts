plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Opt-in micro-benchmark of TracingDispatcher.dispatch overhead. NOT published, NOT part of
// `build`/`check`/CI: it only runs through the explicit `tracingBenchmark` task. The numbers are
// documented in coroutines-runtime/runtime/README.md; nothing asserts on timing.
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":runtime"))
}

tasks.register<JavaExec>("tracingBenchmark") {
    group = "benchmark"
    description = "Measures TracingDispatcher.dispatch overhead (ns/op, bytes/op). Opt-in, not part of check."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.santimattius.structured.runtime.benchmark.TracingBenchmarkKt")
    // Fixed heap so allocation/GC behaviour is comparable between runs.
    jvmArgs("-Xms1g", "-Xmx1g", "-XX:+UseSerialGC")
    // Optional tuning: -PbenchRounds=10 -PbenchOps=2000000 -PbenchWarmup=5
    listOf("benchRounds", "benchOps", "benchWarmup").forEach { key ->
        project.findProperty(key)?.let { systemProperty(key, it.toString()) }
    }
}
