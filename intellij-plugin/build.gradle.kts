/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

plugins {
    alias(libs.plugins.kotlin.jvm)
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "io.github.santimattius"
version = "0.2.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(libs.versions.intellij.ide.get())
        bundledPlugin("org.jetbrains.kotlin")
        pluginVerifier()
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
}

intellijPlatform {
    pluginConfiguration {
        id = "io.github.santimattius.structured-coroutines"
        name = "Structured Coroutines"
        version = project.version.toString()
        description = """
            <p>Enforces structured concurrency best practices for Kotlin Coroutines.</p>
            <p>Features:</p>
            <ul>
                <li>Real-time inspections for coroutine anti-patterns</li>
                <li>Quick fixes for automatic code correction</li>
                <li>Intentions for code refactoring</li>
                <li>Gutter icons for scope and dispatcher visualization</li>
            </ul>
        """.trimIndent()
        changeNotes = """
            <p><b>v0.2.0</b></p>
            <ul>
                <li>IDE compatibility: IntelliJ IDEA 2024.3–2025.x (builds 243–252)</li>
                <li>11 inspections for coroutine best practices</li>
                <li>9 quick fixes for automatic corrections</li>
                <li>5 intentions for refactoring</li>
                <li>2 gutter icon providers</li>
                <li>Structured Coroutines tool window</li>
                <li>Full K2 compiler mode support</li>
            </ul>
        """.trimIndent()
        ideaVersion {
            sinceBuild = "243"
            untilBuild = "252.*"
        }
        vendor {
            name = "Santiago Mattiauda"
            url = "https://github.com/santimattius/structured-coroutines"
            email = "santimattius@gmail.com"
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }

    publishing {
        token.set(providers.gradleProperty("marketplaceToken")
            .orElse(providers.environmentVariable("JB_MARKETPLACE_TOKEN")))

        // El "channel" por defecto es "default" (estable).
        // Puedes usar "EAP" o "Beta" si quieres probar antes.
        channels.set(listOf("default"))
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
