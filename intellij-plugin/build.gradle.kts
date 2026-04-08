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
    id("org.jetbrains.intellij.platform") version libs.versions.intellij.platform.get()
}

group = "io.github.santimattius"
version = "0.7.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaUltimate(libs.versions.intellij.ide.get())
        bundledPlugin("org.jetbrains.kotlin")
        bundledPlugin("com.intellij.java")
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
            <p><b>v0.7.0</b></p>
            <ul>
                <li><b>IDE compatibility extended</b> — Now supports IntelliJ IDEA 2026.1 (build 261.*) and Android Studio Panda 2025.3.3 (build 253.*)</li>
            </ul>
            <p><b>v0.6.1</b></p>
            <ul>
                <li><b>Local framework scope recognition (compiler + IDE + lint)</b> — Scopes created via framework helpers (e.g. <code>rememberCoroutineScope()</code>) stored in local variables are now treated as structured scopes, eliminating false positives in UnstructuredLaunch across all three layers</li>
                <li><b>@Suppress / @file:Suppress support</b> — All IntelliJ inspections now honor <code>@Suppress("RuleName")</code> on the flagged element and <code>@file:Suppress</code> at the file level via <code>CoroutineInspectionBase</code></li>
                <li><b>GlobalScopeInspection short name fix</b> — Overrides <code>getShortName()</code> to align the runtime ID with the registered inspection ID, fixing suppression not working</li>
                <li><b>Lint parenthesized-receiver fix</b> — <code>CoroutineLintUtils</code> unwraps parenthesized receivers and uses a PSI fallback to detect call- and name-based framework scopes; new helper traces local variable initializers</li>
            </ul>
            <p><b>v0.6.0</b></p>
            <ul>
                <li><b>Scan Project action</b> — New "Scan Project for Coroutine Issues" action in the Analyze menu and tool window toolbar; runs all 13 inspections across every Kotlin source file in the background with real-time progress</li>
                <li><b>False-positive guard (CoroutinesImportFilter)</b> — All 13 inspections now exit early when a file does not import kotlinx.coroutines, eliminating false positives from name collisions with non-coroutines APIs (e.g. ActivityScenario.launch)</li>
                <li><b>SuspendInFinallyInspection</b> — Replaced brittle hard-coded name blocklist with proper suspend-call detection via type resolution</li>
                <li><b>structuredCoroutinesReport Gradle task</b> — Generates HTML and plain-text reports of the active compiler-plugin configuration (rule codes, severities, exclusions) for CI auditing</li>
                <li><b>CI integration guide</b> — New public guide (docs/CI_INTEGRATION.md) with full GitHub Actions workflow, SARIF upload, artifact archiving, and PR comment automation</li>
            </ul>
            <p><b>v0.5.0</b></p>
            <ul>
                <li><b>WithTimeoutScopeCancellation (CANCEL_006)</b> — New inspection: withTimeout without handling TimeoutCancellationException may cancel the parent scope; quick fix to replace with withTimeoutOrNull</li>
                <li><b>Tool window — "What to do"</b> — Each finding now shows a 1–2 line action summary and a "See guide" link to the relevant section in BEST_PRACTICES_COROUTINES.md</li>
                <li><b>async description (SCOPE_002)</b> — Inspection description extended with §5.3 EXCEPT_003 context: exceptions in async are stored in the Deferred and not sent to CoroutineExceptionHandler</li>
                <li><b>Decision Guide</b> — New public guide (docs/DECISION_GUIDE.md) with decision tables and trees: launch vs async, which scope, viewModelScope vs lifecycleScope, runTest vs runBlocking, dispatchers, withTimeout, loops, cold vs hot Flow</li>
            </ul>
            <p><b>v0.4.1</b></p>
            <ul>
                <li>IDE compatibility: extended support for build 253.* (e.g. Android Studio / IntelliJ AI-253.x)</li>
            </ul>
            <p><b>v0.4.0</b></p>
            <ul>
                <li><b>LoopWithoutYield (CANCEL_001)</b> — New inspection for loops in suspend functions without cooperation points; quick fixes to insert ensureActive(), currentCoroutineContext().ensureActive(), yield(), or delay(0)</li>
                <li><b>ScopeReuseAfterCancel (CANCEL_005)</b> — Reinforced inspection and messages; error text now guides to the quick fix (replace cancel() with cancelChildren())</li>
                <li><b>LifecycleAwareFlowCollection (ARCH_002)</b> — New inspection: Flow collection in lifecycleScope.launch without repeatOnLifecycle/flowWithLifecycle</li>
                <li><b>Convert to runTest</b> — New intention: replace runBlocking { } with runTest when the body contains delay() (TEST_001, virtual time for tests)</li>
                <li><b>CancellationException subclass</b> — New quick fix: change superclass from CancellationException to Exception for domain errors (4.2)</li>
            </ul>
            <p><b>v0.3.0</b></p>
            <ul>
                <li>12 inspections: GlobalScope, runBlocking in suspend, async/await, Job in builder, suspend in finally, CancellationException, Dispatchers.Unconfined, ScopeReuseAfterCancel, UnstructuredLaunch, MainDispatcherMisuse</li>
                <li>9 quick fixes: Replace cancel with cancelChildren, Wrap with NonCancellable, Replace GlobalScope, Add await, and more</li>
                <li>5 intentions: Migrate to lifecycleScope/viewModelScope, Convert launch to async, Wrap with coroutineScope, Extract suspend function</li>
                <li>Tool window and gutter icons for scope and dispatcher</li>
            </ul>
            <p><b>v0.2.0</b></p>
            <ul>
                <li>IDE compatibility: IntelliJ IDEA 2024.3–2025.x (builds 243–252)</li>
                <li>13 inspections for coroutine best practices</li>
                <li>9 quick fixes for automatic corrections</li>
                <li>5 intentions for refactoring</li>
                <li>2 gutter icon providers</li>
                <li>Structured Coroutines tool window</li>
                <li>Full K2 compiler mode support</li>
            </ul>
        """.trimIndent()
        ideaVersion {
            sinceBuild = "243"
            untilBuild = "261.*"
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
        channels.set(listOf("default"))
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
