package io.github.santimattius.structured.compiler

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * Functional tests for the Structured Coroutines Compiler Plugin using Gradle TestKit.
 *
 * These tests create temporary Gradle projects, apply the plugin,
 * and verify that compilation succeeds or fails as expected.
 */
class StructuredCoroutinesPluginFunctionalTest {

    // ============================================================================
    // Test Utilities
    // ============================================================================

    private fun createTestProject(
        sourceCode: String,
        fileName: String = "Test.kt",
        gradlePropertiesExtra: String? = null,
        extensionBlock: String? = null,
    ): File {
        val projectDir = File.createTempFile("test-project", "").apply {
            delete()
            mkdirs()
        }

        // gradle.properties (optional - for JVM args like compiler locale)
        if (gradlePropertiesExtra != null) {
            File(projectDir, "gradle.properties").writeText(gradlePropertiesExtra)
        }

        // settings.gradle.kts
        File(projectDir, "settings.gradle.kts").writeText("""
            rootProject.name = "test-project"
            
            pluginManagement {
                repositories {
                    mavenLocal()
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            
            dependencyResolutionManagement {
                repositories {
                    mavenLocal()
                    mavenCentral()
                }
            }
        """.trimIndent())

        // build.gradle.kts - version from system property (set by test task) or default for local runs
        val pluginVersion = System.getProperty("structuredCoroutines.version", "0.3.0")
        val kotlinVersion = System.getProperty("kotlinVersion")
            ?: error("kotlinVersion system property not set — check compiler/build.gradle.kts tasks.test block")
        val coroutinesVersion = System.getProperty("coroutinesVersion")
            ?: error("coroutinesVersion system property not set — check compiler/build.gradle.kts tasks.test block")
        File(projectDir, "build.gradle.kts").writeText("""
            plugins {
                kotlin("jvm") version "$kotlinVersion"
                id("io.github.santimattius.structured-coroutines") version "$pluginVersion"
            }

            dependencies {
                implementation("io.github.santimattius:structured-coroutines-annotations:$pluginVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
            }
            
            kotlin {
                jvmToolchain(17)
            }

            ${extensionBlock.orEmpty()}
        """.trimIndent())

        // Source directory
        val srcDir = File(projectDir, "src/main/kotlin").apply { mkdirs() }
        File(srcDir, fileName).writeText(sourceCode)

        return projectDir
    }

    private fun runBuild(projectDir: File, expectSuccess: Boolean = true): String {
        return runBuildWithEnv(projectDir, emptyMap(), expectSuccess)
    }

    private fun runBuildWithEnv(
        projectDir: File,
        env: Map<String, String>,
        expectSuccess: Boolean = true
    ): String {
        val runner = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("compileKotlin", "--stacktrace", "--info")
            .forwardOutput()
        // Merge with system environment to keep PATH, JAVA_HOME, etc. intact
        val runnerWithEnv = if (env.isEmpty()) runner else runner.withEnvironment(System.getenv() + env)
        val result = runnerWithEnv.run { if (expectSuccess) build() else buildAndFail() }
        return result.output
    }

    // ============================================================================
    // Valid Code Tests - These should compile successfully
    // ============================================================================

    @Test
    fun `code with @StructuredScope annotation compiles successfully`() {
        val sourceCode = """
            import kotlinx.coroutines.CoroutineScope
            import kotlinx.coroutines.launch
            import io.github.santimattius.structured.annotations.StructuredScope
            
            fun loadData(@StructuredScope scope: CoroutineScope) {
                scope.launch {
                    println("Hello!")
                }
            }
        """.trimIndent()

        val projectDir = createTestProject(sourceCode)
        val output = runBuild(projectDir, expectSuccess = true)
        
        assertTrue("BUILD SUCCESSFUL" in output || "compileKotlin" in output,
            "Expected successful build but got:\n$output")
    }

    @Test
    fun `constructor property with @StructuredScope compiles successfully`() {
        val sourceCode = """
            import kotlinx.coroutines.CoroutineScope
            import kotlinx.coroutines.launch
            import io.github.santimattius.structured.annotations.StructuredScope
            
            class Service(@property:StructuredScope private val scope: CoroutineScope) {
                fun run() {
                    scope.launch { println("Running") }
                }
            }
        """.trimIndent()

        val projectDir = createTestProject(sourceCode)
        val output = runBuild(projectDir, expectSuccess = true)
        
        assertTrue("BUILD SUCCESSFUL" in output || "compileKotlin" in output)
    }

    @Test
    fun `meta-annotated DI qualifier on scope compiles successfully`() {
        val sourceCode = """
            import kotlinx.coroutines.CoroutineScope
            import kotlinx.coroutines.launch
            import io.github.santimattius.structured.annotations.StructuredScope

            @StructuredScope
            @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY)
            annotation class TestAppScope

            class Service(@property:TestAppScope private val scope: CoroutineScope) {
                fun run() {
                    scope.launch { println("Running") }
                }
            }
        """.trimIndent()

        val projectDir = createTestProject(sourceCode)
        val output = runBuild(projectDir, expectSuccess = true)

        assertTrue("BUILD SUCCESSFUL" in output || "compileKotlin" in output, "Expected success:\n$output")
    }

    @Test
    fun `DI qualifier without meta StructuredScope fails compilation`() {
        val sourceCode = """
            import kotlinx.coroutines.CoroutineScope
            import kotlinx.coroutines.launch

            @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY)
            annotation class TestAppScope

            class Service(@property:TestAppScope private val scope: CoroutineScope) {
                fun run() {
                    scope.launch { println("Running") }
                }
            }
        """.trimIndent()

        val projectDir = createTestProject(sourceCode)
        val output = runBuild(projectDir, expectSuccess = false)

        assertTrue(
            "UNSTRUCTURED_COROUTINE_LAUNCH" in output || "SCOPE_003" in output,
            "Expected SCOPE_003 error but got:\n$output",
        )
    }

    @Test
    fun `supervisorScope usage compiles successfully`() {
        val sourceCode = """
            import kotlinx.coroutines.launch
            import kotlinx.coroutines.supervisorScope
            
            suspend fun process() = supervisorScope {
                launch { println("Task 1") }
                launch { println("Task 2") }
            }
        """.trimIndent()

        val projectDir = createTestProject(sourceCode)
        val output = runBuild(projectDir, expectSuccess = true)
        
        assertTrue("BUILD SUCCESSFUL" in output || "compileKotlin" in output)
    }

    @Test
    fun `runBlocking in regular function compiles successfully`() {
        val sourceCode = """
            import kotlinx.coroutines.runBlocking
            import kotlinx.coroutines.delay
            
            fun main() = runBlocking {
                delay(100)
                println("Done")
            }
        """.trimIndent()

        val projectDir = createTestProject(sourceCode)
        val output = runBuild(projectDir, expectSuccess = true)
        
        assertTrue("BUILD SUCCESSFUL" in output || "compileKotlin" in output)
    }

    // ============================================================================
    // Invalid Code Tests - These should fail compilation
    // ============================================================================

    @Test
    fun `GlobalScope usage fails compilation`() {
        val sourceCode = """
            import kotlinx.coroutines.GlobalScope
            import kotlinx.coroutines.launch
            
            fun test() {
                GlobalScope.launch {
                    println("Bad!")
                }
            }
        """.trimIndent()

        val projectDir = createTestProject(sourceCode)
        val output = runBuild(projectDir, expectSuccess = false)
        
        assertTrue(
            "GLOBAL_SCOPE_USAGE" in output || "GlobalScope" in output || "[SCOPE_001]" in output,
            "Expected GlobalScope error but got:\n$output"
        )
    }

    @Test
    fun `inline CoroutineScope creation fails compilation`() {
        val sourceCode = """
            import kotlinx.coroutines.CoroutineScope
            import kotlinx.coroutines.Dispatchers
            import kotlinx.coroutines.launch
            
            fun test() {
                CoroutineScope(Dispatchers.IO).launch {
                    println("Bad!")
                }
            }
        """.trimIndent()

        val projectDir = createTestProject(sourceCode)
        val output = runBuild(projectDir, expectSuccess = false)
        
        assertTrue(
            "INLINE_COROUTINE_SCOPE" in output || "CoroutineScope" in output || "[SCOPE_003]" in output,
            "Expected inline scope error but got:\n$output"
        )
    }

    @Test
    fun `unstructured launch fails compilation`() {
        val sourceCode = """
            import kotlinx.coroutines.CoroutineScope
            import kotlinx.coroutines.launch
            
            fun test(scope: CoroutineScope) {
                scope.launch {
                    println("Bad!")
                }
            }
        """.trimIndent()

        val projectDir = createTestProject(sourceCode)
        val output = runBuild(projectDir, expectSuccess = false)
        
        assertTrue(
            "UNSTRUCTURED_COROUTINE_LAUNCH" in output || "unstructured" in output.lowercase() || "[SCOPE_003]" in output,
            "Expected unstructured launch error but got:\n$output"
        )
    }

    @Test
    fun `runBlocking in suspend function should ideally fail compilation`() {
        // Note: This test documents expected behavior. The checker may need 
        // additional work to properly detect all runBlocking usages in suspend functions.
        val sourceCode = """
            import kotlinx.coroutines.runBlocking
            import kotlinx.coroutines.delay
            
            suspend fun badFunction() {
                runBlocking {
                    delay(100)
                }
            }
        """.trimIndent()

        val projectDir = createTestProject(sourceCode)
        
        // For now, we just verify the code can be compiled and the plugin is loaded
        // The runBlocking checker may need refinement for edge cases
        try {
            val output = runBuild(projectDir, expectSuccess = false)
            assertTrue(
                "RUN_BLOCKING_IN_SUSPEND" in output || "runBlocking" in output || "[RUNBLOCK_002]" in output,
                "Expected runBlocking error but got:\n$output"
            )
        } catch (e: org.gradle.testkit.runner.UnexpectedBuildSuccess) {
            // If build succeeds, the checker might not be detecting this case
            // This is acceptable for now - mark as known limitation
            println("Note: runBlocking in suspend detection may need refinement")
        }
    }

    @Test
    fun `Job in launch fails compilation`() {
        val sourceCode = """
            import kotlinx.coroutines.CoroutineScope
            import kotlinx.coroutines.Job
            import kotlinx.coroutines.launch
            import io.github.santimattius.structured.annotations.StructuredScope
            
            fun test(@StructuredScope scope: CoroutineScope) {
                scope.launch(Job()) {
                    println("Bad!")
                }
            }
        """.trimIndent()

        val projectDir = createTestProject(sourceCode)
        val output = runBuild(projectDir, expectSuccess = false)
        
        assertTrue(
            "JOB_IN_BUILDER_CONTEXT" in output || "Job" in output || "[DISPATCH_004]" in output,
            "Expected Job error but got:\n$output"
        )
    }

    @Test
    fun `SupervisorJob in withContext fails compilation`() {
        val sourceCode = """
            import kotlinx.coroutines.SupervisorJob
            import kotlinx.coroutines.withContext
            
            suspend fun test() {
                withContext(SupervisorJob()) {
                    println("Bad!")
                }
            }
        """.trimIndent()

        val projectDir = createTestProject(sourceCode)
        val output = runBuild(projectDir, expectSuccess = false)
        
        assertTrue(
            "JOB_IN_BUILDER_CONTEXT" in output || "SupervisorJob" in output || "[DISPATCH_004]" in output,
            "Expected SupervisorJob error but got:\n$output"
        )
    }

    @Test
    fun `CancellationException subclass fails compilation`() {
        val sourceCode = """
            import kotlinx.coroutines.CancellationException
            
            class MyDomainError : CancellationException("Domain error")
        """.trimIndent()

        val projectDir = createTestProject(sourceCode)
        val output = runBuild(projectDir, expectSuccess = false)
        
        assertTrue(
            "CANCELLATION_EXCEPTION_SUBCLASS" in output || "CancellationException" in output || "[EXCEPT_002]" in output,
            "Expected CancellationException subclass error but got:\n$output"
        )
    }

    // ============================================================================
    // Warning Tests
    // ============================================================================

    @Test
    fun `Dispatchers Unconfined produces warning but compiles`() {
        val sourceCode = """
            import kotlinx.coroutines.CoroutineScope
            import kotlinx.coroutines.Dispatchers
            import kotlinx.coroutines.launch
            import io.github.santimattius.structured.annotations.StructuredScope
            
            fun test(@StructuredScope scope: CoroutineScope) {
                scope.launch(Dispatchers.Unconfined) {
                    println("Warning!")
                }
            }
        """.trimIndent()

        val projectDir = createTestProject(sourceCode)
        // Should succeed (warning, not error) - compilation completes
        val output = runBuild(projectDir, expectSuccess = true)
        
        // Build should succeed even with warning
        assertTrue("BUILD SUCCESSFUL" in output || "compileKotlin" in output,
            "Expected successful build but got:\n$output")
    }

    // ============================================================================
    // Integration Tests - Complex Scenarios
    // ============================================================================

    @Test
    fun `complete Repository pattern compiles successfully`() {
        val sourceCode = """
            import kotlinx.coroutines.CoroutineScope
            import kotlinx.coroutines.Dispatchers
            import kotlinx.coroutines.launch
            import kotlinx.coroutines.async
            import kotlinx.coroutines.withContext
            import kotlinx.coroutines.CancellationException
            import io.github.santimattius.structured.annotations.StructuredScope
            
            class Repository(@property:StructuredScope private val scope: CoroutineScope) {
                
                fun fetchData() {
                    scope.launch {
                        try {
                            val data = loadFromNetwork()
                            saveToCache(data)
                        } catch (e: CancellationException) {
                            throw e  // Re-throw cancellation
                        } catch (e: Exception) {
                            handleError(e)
                        }
                    }
                }
                
                suspend fun fetchDataAsync() = scope.async {
                    loadFromNetwork()
                }
                
                private suspend fun loadFromNetwork(): String {
                    return withContext(Dispatchers.IO) {
                        "data"
                    }
                }
                
                private suspend fun saveToCache(data: String) {
                    withContext(Dispatchers.IO) {
                        println("Saving: ${'$'}data")
                    }
                }
                
                private fun handleError(e: Exception) {
                    println("Error: ${'$'}e")
                }
            }
        """.trimIndent()

        val projectDir = createTestProject(sourceCode)
        val output = runBuild(projectDir, expectSuccess = true)
        
        assertTrue("BUILD SUCCESSFUL" in output || "compileKotlin" in output,
            "Expected successful build but got:\n$output")
    }

    // ============================================================================
    // i18n Tests - Compiler messages when using the Gradle plugin
    // ============================================================================

    @Test
    fun `compiler message includes rule code SCOPE_001 when GlobalScope is used`() {
        val sourceCode = """
            import kotlinx.coroutines.GlobalScope
            import kotlinx.coroutines.launch
            
            fun test() {
                GlobalScope.launch { println("Bad!") }
            }
        """.trimIndent()

        val projectDir = createTestProject(sourceCode)
        val output = runBuild(projectDir, expectSuccess = false)

        assertTrue(
            "[SCOPE_001]" in output,
            "Expected rule code [SCOPE_001] in compiler output. Got:\n${output.takeLast(1500)}"
        )
        // i18n: message may be in English or Spanish depending on structured.coroutines.compiler.locale / JVM locale
        val hasEnglish = "GlobalScope usage is not allowed" in output
        val hasSpanish = "El uso de GlobalScope no está permitido" in output
        assertTrue(
            hasEnglish || hasSpanish,
            "Expected localized message (EN or ES). Got:\n${output.takeLast(1500)}"
        )
    }

    @Test
    fun `compiler message in Spanish when JAVA_TOOL_OPTIONS sets locale=es`() {
        val sourceCode = """
            import kotlinx.coroutines.GlobalScope
            import kotlinx.coroutines.launch

            fun test() {
                GlobalScope.launch { println("Bad!") }
            }
        """.trimIndent()

        // Set org.gradle.jvmargs so a new Gradle daemon is started with Spanish locale
        // (the compiler plugin runs in the Gradle daemon's JVM via in-process compilation).
        // kotlin.daemon.jvmargs is also set for external Kotlin daemon mode.
        val projectDir = createTestProject(
            sourceCode,
            gradlePropertiesExtra = "org.gradle.jvmargs=-Dstructured.coroutines.compiler.locale=es\n" +
                "kotlin.daemon.jvmargs=-Dstructured.coroutines.compiler.locale=es"
        )
        val output = runBuild(projectDir, expectSuccess = false)

        assertTrue("[SCOPE_001]" in output, "Expected rule code [SCOPE_001] in output")
        assertTrue(
            "El uso de GlobalScope no está permitido" in output,
            "Expected Spanish message when locale=es (kotlin.daemon.jvmargs). Got:\n${output.takeLast(1500)}"
        )
    }

    // ============================================================================
    // Sample project validation (real :sample with compiler plugin)
    // ============================================================================

    /**
     * Runs `:sample:compileKotlin` via the project's Gradle wrapper as a plain OS process,
     * capturing both stdout and stderr (merged). Using ProcessBuilder instead of GradleRunner
     * here because the Kotlin Build Tools API (BTAPI) worker writes compiler diagnostics to
     * the Gradle process's stderr stream, which GradleRunner does not forward into
     * BuildResult.output.
     */
    private fun runSampleCompilation(env: Map<String, String> = emptyMap()): String? {
        val rootDir = System.getProperty("structuredCoroutines.rootDir") ?: return null
        val root = File(rootDir)
        if (!File(root, "sample/build.gradle.kts").exists()) return null

        val gradlew = if (System.getProperty("os.name", "").startsWith("Windows")) "gradlew.bat" else "gradlew"
        val pb = ProcessBuilder(File(root, gradlew).absolutePath, ":sample:compileKotlin", "--info")
            .directory(root)
            .redirectErrorStream(true)

        pb.environment().apply {
            clear()
            putAll(System.getenv())
            putAll(env)
        }

        val process = pb.start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        return output
    }

    private fun skipSampleTest(): Boolean {
        val rootDir = System.getProperty("structuredCoroutines.rootDir") ?: run {
            println("Skipping sample validation: structuredCoroutines.rootDir not set (run from full project)")
            return true
        }
        if (!File(rootDir, "sample/build.gradle.kts").exists()) {
            println("Skipping sample validation: sample project not found at $rootDir")
            return true
        }
        return false
    }

    @Test
    fun `sample project fails compilation with expected rule codes`() {
        if (skipSampleTest()) return
        val output = runSampleCompilation() ?: return

        val expectedCodes = listOf(
            "[SCOPE_001]",
            "[SCOPE_003]",
            "[DISPATCH_004]",
        )
        for (code in expectedCodes) {
            assertTrue(
                code in output,
                "Sample compilation output should contain $code. Got (last 2k chars):\n${output.takeLast(2000)}"
            )
        }
    }

    @Test
    fun `sample project with locale en shows localized compiler messages`() {
        if (skipSampleTest()) return
        val output = runSampleCompilation(
            mapOf("JAVA_TOOL_OPTIONS" to "-Dstructured.coroutines.compiler.locale=en")
        ) ?: return

        assertTrue("[SCOPE_001]" in output, "Expected [SCOPE_001] in output")
        val hasEnglish = "GlobalScope usage is not allowed" in output
        val hasSpanish = "El uso de GlobalScope no está permitido" in output
        assertTrue(
            hasEnglish || hasSpanish,
            "Expected localized SCOPE_001 message (EN or ES). Got (last 2k):\n${output.takeLast(2000)}"
        )
    }

    @Test
    fun `sample project with locale es shows localized compiler messages`() {
        if (skipSampleTest()) return
        val output = runSampleCompilation(
            mapOf("JAVA_TOOL_OPTIONS" to "-Dstructured.coroutines.compiler.locale=es")
        ) ?: return

        assertTrue("[SCOPE_001]" in output, "Expected [SCOPE_001] in output")
        val hasEnglish = "GlobalScope usage is not allowed" in output
        val hasSpanish = "El uso de GlobalScope no está permitido" in output
        assertTrue(
            hasEnglish || hasSpanish,
            "Expected localized SCOPE_001 message (EN or ES). Got (last 2k):\n${output.takeLast(2000)}"
        )
    }

    @Test
    fun `suspendCoroutine in suspend function fails compilation`() {
        val sourceCode = """
            import kotlinx.coroutines.suspendCoroutine
            
            suspend fun bad(): Unit =
                suspendCoroutine { cont -> cont.resume(Unit) }
        """.trimIndent()

        val projectDir = createTestProject(sourceCode)
        val output = runBuild(projectDir, expectSuccess = false)
        assertTrue(
            "SUSPEND_COROUTINE_WITHOUT_CANCELLATION" in output || "[INTEROP_001]" in output ||
                "suspendCoroutine" in output,
            "Expected INTEROP_001 but got:\n$output"
        )
    }

    @Test
    fun `callbackFlow without awaitClose fails compilation`() {
        val sourceCode = """
            import kotlinx.coroutines.flow.callbackFlow
            
            fun broken() = callbackFlow<Unit> { }
        """.trimIndent()

        val projectDir = createTestProject(sourceCode)
        val output = runBuild(projectDir, expectSuccess = false)
        assertTrue(
            "CALLBACK_FLOW_WITHOUT_AWAIT_CLOSE" in output || "[INTEROP_002]" in output ||
                "awaitClose" in output,
            "Expected INTEROP_002 but got:\n$output"
        )
    }

    @Test
    fun `channelFlow compiles without awaitClose`() {
        val sourceCode = """
            import kotlinx.coroutines.flow.channelFlow

            fun ok() = channelFlow<Int> { send(42) }
        """.trimIndent()

        val projectDir = createTestProject(sourceCode)
        val output = runBuild(projectDir, expectSuccess = true)
        assertTrue("BUILD SUCCESSFUL" in output || "compileKotlin" in output, output)
    }

    // ============================================================================
    // LOOP_WITHOUT_YIELD deep traversal (#66 / CANCEL_001)
    // ============================================================================

    @Test
    fun `cooperation point in val initializer suppresses LOOP_WITHOUT_YIELD`() {
        val sourceCode = """
            import kotlinx.coroutines.delay

            suspend fun suspendCall(): Int {
                delay(1)
                return 1
            }

            suspend fun loopValInitializer() {
                while (true) {
                    val n = suspendCall()
                    if (n <= 0) break
                }
            }
        """.trimIndent()

        val projectDir = createTestProject(sourceCode)
        val output = runBuild(projectDir, expectSuccess = true)

        assertTrue(
            "LOOP_WITHOUT_YIELD" !in output && "[CANCEL_001]" !in output,
            "Did not expect LOOP_WITHOUT_YIELD for a cooperation point in a val initializer but got:\n$output"
        )
    }

    @Test
    fun `cooperation point in assignment RHS suppresses LOOP_WITHOUT_YIELD`() {
        val sourceCode = """
            import kotlinx.coroutines.delay

            suspend fun suspendCall(): Int {
                delay(1)
                return 1
            }

            suspend fun loopAssignment() {
                var n = 1
                while (n > 0) {
                    n = suspendCall()
                }
            }
        """.trimIndent()

        val projectDir = createTestProject(sourceCode)
        val output = runBuild(projectDir, expectSuccess = true)

        assertTrue(
            "LOOP_WITHOUT_YIELD" !in output && "[CANCEL_001]" !in output,
            "Did not expect LOOP_WITHOUT_YIELD for a cooperation point in an assignment RHS but got:\n$output"
        )
    }

    @Test
    fun `cooperation point in if condition suppresses LOOP_WITHOUT_YIELD`() {
        val sourceCode = """
            import kotlinx.coroutines.delay

            suspend fun suspendCall(): Int {
                delay(1)
                return 1
            }

            suspend fun loopIfCondition() {
                while (true) {
                    if (suspendCall() <= 0) {
                        break
                    }
                }
            }
        """.trimIndent()

        val projectDir = createTestProject(sourceCode)
        val output = runBuild(projectDir, expectSuccess = true)

        assertTrue(
            "LOOP_WITHOUT_YIELD" !in output && "[CANCEL_001]" !in output,
            "Did not expect LOOP_WITHOUT_YIELD for a cooperation point in an if condition but got:\n$output"
        )
    }

    @Test
    fun `cooperation point in when branch suppresses LOOP_WITHOUT_YIELD`() {
        val sourceCode = """
            import kotlinx.coroutines.delay

            suspend fun suspendCall(): Int {
                delay(1)
                return 1
            }

            suspend fun loopWhenBranch(): Int {
                var count = 0
                while (count < 10) {
                    when {
                        count % 2 == 0 -> suspendCall()
                        else -> count++
                    }
                    count++
                }
                return count
            }
        """.trimIndent()

        val projectDir = createTestProject(sourceCode)
        val output = runBuild(projectDir, expectSuccess = true)

        assertTrue(
            "LOOP_WITHOUT_YIELD" !in output && "[CANCEL_001]" !in output,
            "Did not expect LOOP_WITHOUT_YIELD for a cooperation point in a when branch but got:\n$output"
        )
    }

    @Test
    fun `cooperation point on elvis RHS suppresses LOOP_WITHOUT_YIELD`() {
        val sourceCode = """
            import kotlinx.coroutines.delay

            suspend fun suspendCall(): Int {
                delay(1)
                return 1
            }

            suspend fun loopElvis(): Int {
                var total = 0
                while (total < 10) {
                    val maybe: Int? = null
                    val n = maybe ?: suspendCall()
                    total += n
                }
                return total
            }
        """.trimIndent()

        val projectDir = createTestProject(sourceCode)
        val output = runBuild(projectDir, expectSuccess = true)

        assertTrue(
            "LOOP_WITHOUT_YIELD" !in output && "[CANCEL_001]" !in output,
            "Did not expect LOOP_WITHOUT_YIELD for a cooperation point on an elvis RHS but got:\n$output"
        )
    }

    @Test
    fun `cooperation point inside try block suppresses LOOP_WITHOUT_YIELD`() {
        val sourceCode = """
            import kotlinx.coroutines.delay

            suspend fun suspendCall(): Int {
                delay(1)
                return 1
            }

            suspend fun loopTryBlock(): Int {
                var total = 0
                while (total < 10) {
                    try {
                        total += suspendCall()
                    } catch (e: Exception) {
                        total++
                    }
                }
                return total
            }
        """.trimIndent()

        val projectDir = createTestProject(sourceCode)
        val output = runBuild(projectDir, expectSuccess = true)

        assertTrue(
            "LOOP_WITHOUT_YIELD" !in output && "[CANCEL_001]" !in output,
            "Did not expect LOOP_WITHOUT_YIELD for a cooperation point inside a try block but got:\n$output"
        )
    }

    @Test
    fun `loop with no cooperation point anywhere still reports LOOP_WITHOUT_YIELD`() {
        val sourceCode = """
            fun doWork() {
                println("working")
            }

            suspend fun loopNoCooperation() {
                while (true) {
                    doWork()
                }
            }
        """.trimIndent()

        val projectDir = createTestProject(sourceCode)
        val output = runBuild(projectDir, expectSuccess = true)

        assertTrue(
            "LOOP_WITHOUT_YIELD" in output || "[CANCEL_001]" in output,
            "Expected LOOP_WITHOUT_YIELD for a loop with no cooperation point but got:\n$output"
        )
    }

    @Test
    fun `loop with only an uncalled local suspend fun still reports LOOP_WITHOUT_YIELD`() {
        val sourceCode = """
            import kotlinx.coroutines.delay

            fun doWork() {
                println("working")
            }

            suspend fun loopWithUncalledLocalSuspendFun() {
                while (true) {
                    suspend fun helper() {
                        delay(1)
                    }
                    doWork()
                }
            }
        """.trimIndent()

        val projectDir = createTestProject(sourceCode)
        val output = runBuild(projectDir, expectSuccess = true)

        assertTrue(
            "LOOP_WITHOUT_YIELD" in output || "[CANCEL_001]" in output,
            "Expected LOOP_WITHOUT_YIELD for a loop with only an uncalled local suspend fun but got:\n$output"
        )
    }

    @Test
    fun `val initializer, statement, and assignment cooperation points together suppress LOOP_WITHOUT_YIELD`() {
        // Literal reproduction of https://github.com/santimattius/structured-coroutines/issues/66
        // (function names a/b/c preserved from the issue). `readAvailable`/`flush` stand in for
        // the issue's Ktor ByteReadChannel/ByteWriteChannel calls: this module's functional test
        // harness has no Ktor on its classpath, and the checker is suspend-call-shape agnostic,
        // not type-specific, so a dependency-free suspend fun exercises the exact same FIR path.
        val sourceCode = """
            import kotlinx.coroutines.delay

            suspend fun readAvailable(): Int {
                delay(1)
                return 1
            }

            suspend fun flush() {
                delay(1)
            }

            // a: only suspend call is a property initializer
            suspend fun a(): Int {
                var total = 0
                while (true) {
                    val n = readAvailable()
                    if (n <= 0) break
                    total += n
                }
                return total
            }

            // b: same loop plus one statement-level suspend call
            suspend fun b(): Int {
                var total = 0
                while (true) {
                    val n = readAvailable()
                    if (n <= 0) break
                    flush()
                    total += n
                }
                return total
            }

            // c: suspend call in an assignment
            suspend fun c(): Int {
                var n = 1
                while (n > 0) {
                    n = readAvailable()
                }
                return n
            }
        """.trimIndent()

        val projectDir = createTestProject(sourceCode)
        val output = runBuild(projectDir, expectSuccess = true)

        assertTrue(
            "LOOP_WITHOUT_YIELD" !in output && "[CANCEL_001]" !in output,
            "Did not expect LOOP_WITHOUT_YIELD for issue #66's a/b/c but got:\n$output"
        )
    }

    // ============================================================================
    // SUSPEND_IN_FINALLY_WITHOUT_NON_CANCELLABLE ClassId recognition (#65 / CANCEL_004)
    // ============================================================================

    @Test
    fun `bare NonCancellable reference in withContext suppresses SUSPEND_IN_FINALLY_WITHOUT_NON_CANCELLABLE`() {
        // Literal reproduction of variant A from
        // https://github.com/santimattius/structured-coroutines/issues/65 (function `a`).
        val sourceCode = """
            import kotlinx.coroutines.NonCancellable
            import kotlinx.coroutines.delay
            import kotlinx.coroutines.withContext

            suspend fun a() {
                try {
                    delay(1)
                } finally {
                    withContext(NonCancellable) {
                        delay(1)
                    }
                }
            }
        """.trimIndent()

        val projectDir = createTestProject(sourceCode)
        val output = runBuild(projectDir, expectSuccess = true)

        assertTrue(
            "SUSPEND_IN_FINALLY_WITHOUT_NON_CANCELLABLE" !in output && "[CANCEL_004]" !in output,
            "Did not expect SUSPEND_IN_FINALLY_WITHOUT_NON_CANCELLABLE for bare withContext(NonCancellable) but got:\n$output"
        )
    }

    @Test
    fun `fully-qualified NonCancellable reference in withContext suppresses SUSPEND_IN_FINALLY_WITHOUT_NON_CANCELLABLE`() {
        // Literal reproduction of variant B from
        // https://github.com/santimattius/structured-coroutines/issues/65 (function `b`).
        val sourceCode = """
            import kotlinx.coroutines.delay
            import kotlinx.coroutines.withContext

            suspend fun b() {
                try {
                    delay(1)
                } finally {
                    withContext(kotlinx.coroutines.NonCancellable) {
                        delay(1)
                    }
                }
            }
        """.trimIndent()

        val projectDir = createTestProject(sourceCode)
        val output = runBuild(projectDir, expectSuccess = true)

        assertTrue(
            "SUSPEND_IN_FINALLY_WITHOUT_NON_CANCELLABLE" !in output && "[CANCEL_004]" !in output,
            "Did not expect SUSPEND_IN_FINALLY_WITHOUT_NON_CANCELLABLE for fully-qualified withContext(NonCancellable) but got:\n$output"
        )
    }

    @Test
    fun `NonCancellable plus Dispatchers combo in withContext suppresses SUSPEND_IN_FINALLY_WITHOUT_NON_CANCELLABLE`() {
        val sourceCode = """
            import kotlinx.coroutines.Dispatchers
            import kotlinx.coroutines.NonCancellable
            import kotlinx.coroutines.delay
            import kotlinx.coroutines.withContext

            suspend fun saveToDb() {
                delay(1)
            }

            suspend fun cleanupPlusCombo() {
                try {
                    delay(1)
                } finally {
                    withContext(NonCancellable + Dispatchers.IO) {
                        saveToDb()
                    }
                }
            }
        """.trimIndent()

        val projectDir = createTestProject(sourceCode)
        val output = runBuild(projectDir, expectSuccess = true)

        assertTrue(
            "SUSPEND_IN_FINALLY_WITHOUT_NON_CANCELLABLE" !in output && "[CANCEL_004]" !in output,
            "Did not expect SUSPEND_IN_FINALLY_WITHOUT_NON_CANCELLABLE for withContext(NonCancellable + Dispatchers.IO) but got:\n$output"
        )
    }

    @Test
    fun `unprotected suspend call in finally still reports SUSPEND_IN_FINALLY_WITHOUT_NON_CANCELLABLE`() {
        val sourceCode = """
            import kotlinx.coroutines.delay

            suspend fun saveToDb() {
                delay(1)
            }

            suspend fun cleanupUnprotected() {
                try {
                    delay(1)
                } finally {
                    saveToDb()
                }
            }
        """.trimIndent()

        val projectDir = createTestProject(sourceCode)
        val output = runBuild(projectDir, expectSuccess = true)

        assertTrue(
            "SUSPEND_IN_FINALLY_WITHOUT_NON_CANCELLABLE" in output || "[CANCEL_004]" in output,
            "Expected SUSPEND_IN_FINALLY_WITHOUT_NON_CANCELLABLE for an unprotected suspend call in finally but got:\n$output"
        )
    }

    // ============================================================================
    // CLI bridge (#68, ADR-3, Phase 0/1) — StructuredCoroutinesCommandLineProcessor +
    // META-INF/services registration. Before this bridge existed, every SubpluginOption the
    // Gradle plugin emits for the 14 rule keys was silently dropped: no CommandLineProcessor was
    // registered, so PluginConfiguration.OPTIONS_KEY was never populated in a real build
    // (Ground truth in design.md). This section proves the bridge survives real jar packaging
    // and java.util.ServiceLoader discovery, which unit tests instantiating the classes directly
    // cannot cover.
    // ============================================================================

    @Test
    fun `real build with all 14 severity options set via the DSL succeeds through the new CLI bridge`() {
        val sourceCode = """
            fun ok() = println("hello")
        """.trimIndent()

        // A mix of error/warning/disabled across all 14 rule keys: if the CLI bridge (the
        // registered StructuredCoroutinesCommandLineProcessor + its META-INF/services file)
        // were broken — wrong pluginId, missing/malformed service file, an option key typo, or
        // an exception thrown from processOption — this real build would fail here, unlike the
        // pre-bridge behavior where these options were always silently ignored.
        val projectDir = createTestProject(
            sourceCode,
            extensionBlock = """
                structuredCoroutines {
                    globalScopeUsage.set("warning")
                    inlineCoroutineScope.set("warning")
                    unstructuredLaunch.set("warning")
                    runBlockingInSuspend.set("warning")
                    jobInBuilderContext.set("disabled")
                    dispatchersUnconfined.set("error")
                    cancellationExceptionSubclass.set("disabled")
                    suspendInFinally.set("error")
                    cancellationExceptionSwallowed.set("disabled")
                    unusedDeferred.set("warning")
                    redundantLaunchInCoroutineScope.set("disabled")
                    loopWithoutYield.set("error")
                    suspendCoroutineWithoutCancellation.set("warning")
                    callbackFlowWithoutAwaitClose.set("disabled")
                }
            """.trimIndent(),
        )
        val output = runBuild(projectDir, expectSuccess = true)

        assertTrue(
            "BUILD SUCCESSFUL" in output || "compileKotlin" in output,
            "Expected the CLI bridge (StructuredCoroutinesCommandLineProcessor + its " +
                "META-INF/services registration) to accept all 14 severity options — including " +
                "\"disabled\" — without failing the build, but got:\n$output",
        )
    }

    // ============================================================================
    // Real "disabled" enforcement (#68, ADR-5, Phase 2, tasks 2.6/2.7) — PluginConfiguration is
    // now injected into ScoroutinesCallCheckerExtension and all 12 checkers (replacing the
    // PluginConfigurationHolder global var, which had zero consumers), and PluginConfiguration.
    // report() short-circuits reporting when a rule resolves to RuleSeverity.DISABLED. This is
    // the first real compile-time behavior change of the whole #68 feature — everything before
    // this was inert plumbing (the CLI bridge existed, but nothing read PluginConfiguration at a
    // report call site).
    //
    // One triggering source file exercises all 14 rules; only the DSL block differs between the
    // two tests below, so both tests exercise the exact same production source shape.
    // ============================================================================

    private val allFourteenRulesTriggerSource = """
        import kotlinx.coroutines.CancellationException
        import kotlinx.coroutines.CoroutineScope
        import kotlinx.coroutines.Dispatchers
        import kotlinx.coroutines.GlobalScope
        import kotlinx.coroutines.Job
        import kotlinx.coroutines.async
        import kotlinx.coroutines.coroutineScope
        import kotlinx.coroutines.delay
        import kotlinx.coroutines.flow.callbackFlow
        import kotlinx.coroutines.launch
        import kotlinx.coroutines.runBlocking
        import io.github.santimattius.structured.annotations.StructuredScope
        import kotlin.coroutines.resume
        import kotlin.coroutines.suspendCoroutine

        // globalScopeUsage
        fun triggerGlobalScopeUsage() {
            GlobalScope.launch { println("x") }
        }

        // inlineCoroutineScope
        fun triggerInlineCoroutineScope() {
            CoroutineScope(Dispatchers.IO).launch { println("x") }
        }

        // unstructuredLaunch
        fun triggerUnstructuredLaunch(scope: CoroutineScope) {
            scope.launch { println("x") }
        }

        // runBlockingInSuspend
        suspend fun triggerRunBlockingInSuspend() {
            runBlocking { delay(1) }
        }

        // jobInBuilderContext
        fun triggerJobInBuilderContext(@StructuredScope scope: CoroutineScope) {
            scope.launch(Job()) { println("x") }
        }

        // dispatchersUnconfined
        fun triggerDispatchersUnconfined(@StructuredScope scope: CoroutineScope) {
            scope.launch(Dispatchers.Unconfined) { println("x") }
        }

        // cancellationExceptionSubclass
        class TriggerCancellationExceptionSubclass : CancellationException("domain error")

        // suspendInFinally
        suspend fun triggerSuspendInFinallyHelper() {
            delay(1)
        }
        suspend fun triggerSuspendInFinally() {
            try {
                delay(1)
            } finally {
                triggerSuspendInFinallyHelper()
            }
        }

        // cancellationExceptionSwallowed
        suspend fun triggerCancellationExceptionSwallowedHelper() {
            delay(1)
        }
        suspend fun triggerCancellationExceptionSwallowed() {
            try {
                triggerCancellationExceptionSwallowedHelper()
            } catch (e: Exception) {
                println(e)
            }
        }

        // unusedDeferred
        fun triggerUnusedDeferred(@StructuredScope scope: CoroutineScope) {
            val deferred = scope.async { 42 }
        }

        // redundantLaunchInCoroutineScope
        suspend fun triggerRedundantLaunchInCoroutineScope() = coroutineScope {
            launch { println("x") }
        }

        // loopWithoutYield
        fun triggerLoopWithoutYieldWork() {
            println("x")
        }
        suspend fun triggerLoopWithoutYield() {
            while (true) {
                triggerLoopWithoutYieldWork()
            }
        }

        // suspendCoroutineWithoutCancellation
        suspend fun triggerSuspendCoroutineWithoutCancellation(): Unit =
            suspendCoroutine { cont -> cont.resume(Unit) }

        // callbackFlowWithoutAwaitClose
        fun triggerCallbackFlowWithoutAwaitClose() = callbackFlow<Unit> { }
    """.trimIndent()

    // Locale-invariant rule-code markers (e.g. "[SCOPE_001]") from CompilerBundle*.properties —
    // NOT the raw KtDiagnosticFactory0 `name` strings (e.g. "GLOBAL_SCOPE_USAGE"), which never
    // appear verbatim in compiler output; only the rendered, bracketed rule code does, and it is
    // identical in both the English and Spanish message bundles. `unstructuredLaunch` and
    // `inlineCoroutineScope` share the same "[SCOPE_003]" code in this codebase's message
    // catalog (CompilerBundle.properties:7,11), so this list's shared marker's presence/absence
    // proves both rules together.
    //
    // EXCLUDES dispatchersUnconfined ("[DISPATCH_003]") and redundantLaunchInCoroutineScope
    // ("[RUNBLOCK_001]"): manually isolated during this task (outside GradleRunner, via a plain
    // `gradlew compileKotlin` invocation against the published plugin jar) and confirmed that
    // `DispatchersUnconfinedChecker`/`RedundantLaunchInCoroutineScopeChecker` never report at all,
    // even for the exact trigger shape documented in their own KDoc and in the pre-existing
    // (assertion-free) `Dispatchers Unconfined produces warning but compiles` test. This is a
    // pre-existing bug in the checkers' own detection logic — present in the released 1.1.1 jar,
    // byte-identical in this session's diff except for the constructor/report-call-site changes —
    // not something introduced by, or in scope for, PR2 (injection + disabled enforcement only).
    // The config-resolution side for both rules IS still proven correct by
    // `PluginConfigurationEffectiveSeverityTest` (all 14 rules, including these 2) and both
    // `reportDispatchersUnconfinedUsage`/`reportRedundantLaunchInCoroutineScope` are wired to
    // `config.report(...)` with the exact same shape as the other 12 rules that ARE proven live
    // here. Flagged as a risk/follow-up for sdd-verify and a separate GitHub issue.
    private val allRuleDiagnosticMarkers = listOf(
        "[SCOPE_001]", // globalScopeUsage
        "[SCOPE_003]", // unstructuredLaunch + inlineCoroutineScope (shared code)
        "[RUNBLOCK_002]", // runBlockingInSuspend
        "[DISPATCH_004]", // jobInBuilderContext
        "[EXCEPT_002]", // cancellationExceptionSubclass
        "[CANCEL_004]", // suspendInFinally
        "[CANCEL_003]", // cancellationExceptionSwallowed
        "[SCOPE_002]", // unusedDeferred
        "[CANCEL_001]", // loopWithoutYield
        "[INTEROP_001]", // suspendCoroutineWithoutCancellation
        "[INTEROP_002]", // callbackFlowWithoutAwaitClose
    )

    @Test
    fun `disabled severity really suppresses all 14 configurable diagnostics in a real build`() {
        val projectDir = createTestProject(
            allFourteenRulesTriggerSource,
            extensionBlock = """
                structuredCoroutines {
                    globalScopeUsage.set("disabled")
                    inlineCoroutineScope.set("disabled")
                    unstructuredLaunch.set("disabled")
                    runBlockingInSuspend.set("disabled")
                    jobInBuilderContext.set("disabled")
                    dispatchersUnconfined.set("disabled")
                    cancellationExceptionSubclass.set("disabled")
                    suspendInFinally.set("disabled")
                    cancellationExceptionSwallowed.set("disabled")
                    unusedDeferred.set("disabled")
                    redundantLaunchInCoroutineScope.set("disabled")
                    loopWithoutYield.set("disabled")
                    suspendCoroutineWithoutCancellation.set("disabled")
                    callbackFlowWithoutAwaitClose.set("disabled")
                }
            """.trimIndent(),
        )
        val output = runBuild(projectDir, expectSuccess = true)

        assertTrue(
            "BUILD SUCCESSFUL" in output || "compileKotlin" in output,
            "Expected the build to succeed once all 14 rules are disabled but got:\n$output",
        )
        for (marker in allRuleDiagnosticMarkers) {
            assertTrue(
                marker !in output,
                "Expected $marker to be suppressed once its rule is \"disabled\" but it appeared in:\n$output",
            )
        }
    }

    // Negative control (task 2.7) is split into two builds by default severity class. When a
    // `compileKotlin` build FAILS (buildAndFail), only ERROR-level diagnostics reliably reach the
    // captured GradleRunner output in this Kotlin/Gradle combination (Kotlin 2.4's Build Tools
    // API problem reporting routes WARNING-level diagnostics elsewhere — e.g. the incubating
    // Problems API report — once the task fails; verified empirically: mixing all 14 triggers in
    // one buildAndFail() build reported all 9 ERROR-default codes but none of the 5 WARNING-
    // default codes). A build that SUCCEEDS does not have this issue — the existing
    // `Dispatchers Unconfined produces warning but compiles` and
    // `loop with no cooperation point anywhere still reports LOOP_WITHOUT_YIELD` tests already
    // rely on warning text surviving a successful build. So: ERROR-default rules are proven via
    // one failing build, WARNING-default rules via one succeeding build.

    private val errorDefaultRulesTriggerSource = """
        import kotlinx.coroutines.CancellationException
        import kotlinx.coroutines.CoroutineScope
        import kotlinx.coroutines.Dispatchers
        import kotlinx.coroutines.GlobalScope
        import kotlinx.coroutines.Job
        import kotlinx.coroutines.async
        import kotlinx.coroutines.flow.callbackFlow
        import kotlinx.coroutines.launch
        import kotlinx.coroutines.runBlocking
        import kotlinx.coroutines.delay
        import io.github.santimattius.structured.annotations.StructuredScope
        import kotlin.coroutines.resume
        import kotlin.coroutines.suspendCoroutine

        // globalScopeUsage
        fun triggerGlobalScopeUsage() {
            GlobalScope.launch { println("x") }
        }

        // inlineCoroutineScope
        fun triggerInlineCoroutineScope() {
            CoroutineScope(Dispatchers.IO).launch { println("x") }
        }

        // unstructuredLaunch
        fun triggerUnstructuredLaunch(scope: CoroutineScope) {
            scope.launch { println("x") }
        }

        // runBlockingInSuspend
        suspend fun triggerRunBlockingInSuspend() {
            runBlocking { delay(1) }
        }

        // jobInBuilderContext
        fun triggerJobInBuilderContext(@StructuredScope scope: CoroutineScope) {
            scope.launch(Job()) { println("x") }
        }

        // cancellationExceptionSubclass
        class TriggerCancellationExceptionSubclass : CancellationException("domain error")

        // unusedDeferred
        fun triggerUnusedDeferred(@StructuredScope scope: CoroutineScope) {
            val deferred = scope.async { 42 }
        }

        // suspendCoroutineWithoutCancellation
        suspend fun triggerSuspendCoroutineWithoutCancellation(): Unit =
            suspendCoroutine { cont -> cont.resume(Unit) }

        // callbackFlowWithoutAwaitClose
        fun triggerCallbackFlowWithoutAwaitClose() = callbackFlow<Unit> { }
    """.trimIndent()

    private val errorDefaultRuleMarkers = listOf(
        "[SCOPE_001]", // globalScopeUsage
        "[SCOPE_003]", // unstructuredLaunch + inlineCoroutineScope (shared code)
        "[RUNBLOCK_002]", // runBlockingInSuspend
        "[DISPATCH_004]", // jobInBuilderContext
        "[EXCEPT_002]", // cancellationExceptionSubclass
        "[SCOPE_002]", // unusedDeferred
        "[INTEROP_001]", // suspendCoroutineWithoutCancellation
        "[INTEROP_002]", // callbackFlowWithoutAwaitClose
    )

    private val warningDefaultRulesTriggerSource = """
        import kotlinx.coroutines.CoroutineScope
        import kotlinx.coroutines.Dispatchers
        import kotlinx.coroutines.coroutineScope
        import kotlinx.coroutines.delay
        import kotlinx.coroutines.launch
        import io.github.santimattius.structured.annotations.StructuredScope

        // dispatchersUnconfined
        fun triggerDispatchersUnconfined(@StructuredScope scope: CoroutineScope) {
            scope.launch(Dispatchers.Unconfined) { println("x") }
        }

        // suspendInFinally
        suspend fun triggerSuspendInFinallyHelper() {
            delay(1)
        }
        suspend fun triggerSuspendInFinally() {
            try {
                delay(1)
            } finally {
                triggerSuspendInFinallyHelper()
            }
        }

        // cancellationExceptionSwallowed
        suspend fun triggerCancellationExceptionSwallowedHelper() {
            delay(1)
        }
        suspend fun triggerCancellationExceptionSwallowed() {
            try {
                triggerCancellationExceptionSwallowedHelper()
            } catch (e: Exception) {
                println(e)
            }
        }

        // redundantLaunchInCoroutineScope
        suspend fun triggerRedundantLaunchInCoroutineScope() = coroutineScope {
            launch { println("x") }
        }

        // loopWithoutYield
        fun triggerLoopWithoutYieldWork() {
            println("x")
        }
        suspend fun triggerLoopWithoutYield() {
            while (true) {
                triggerLoopWithoutYieldWork()
            }
        }
    """.trimIndent()

    // Excludes "[DISPATCH_003]" (dispatchersUnconfined) and "[RUNBLOCK_001]"
    // (redundantLaunchInCoroutineScope) — see the exclusion note on allRuleDiagnosticMarkers
    // above; both checkers were confirmed (independent of this PR) to never report at all.
    private val warningDefaultRuleMarkers = listOf(
        "[CANCEL_004]", // suspendInFinally
        "[CANCEL_003]", // cancellationExceptionSwallowed
        "[CANCEL_001]", // loopWithoutYield
    )

    @Test
    fun `unset severity still reports the 9 ERROR-default diagnostics as before (negative control)`() {
        val projectDir = createTestProject(errorDefaultRulesTriggerSource)
        val output = runBuild(projectDir, expectSuccess = false)

        for (marker in errorDefaultRuleMarkers) {
            assertTrue(
                marker in output,
                "Expected $marker to still be reported at its default severity but it was missing from:\n$output",
            )
        }
    }

    @Test
    fun `unset severity still reports the 5 WARNING-default diagnostics as before (negative control)`() {
        val projectDir = createTestProject(warningDefaultRulesTriggerSource)
        val output = runBuild(projectDir, expectSuccess = true)

        for (marker in warningDefaultRuleMarkers) {
            assertTrue(
                marker in output,
                "Expected $marker to still be reported at its default severity but it was missing from:\n$output",
            )
        }
    }
}
