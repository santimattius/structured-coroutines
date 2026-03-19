# Structured Coroutines in CI

This guide explains how to integrate the **Structured Coroutines** toolkit into a CI pipeline (GitHub Actions or any equivalent). The goal is for every pull request to receive automatic feedback on structured-concurrency violations before merging.

The toolkit produces three kinds of report depending on the layer:

| Tool | Report task | Formats |
|------|-------------|---------|
| Compiler plugin (`:compiler` + `:gradle-plugin`) | `structuredCoroutinesReport` | HTML, plain text |
| Detekt (`:detekt-rules`) | `detekt` (built-in reporting) | HTML, XML, SARIF |
| Android Lint (`:lint-rules`) | `lint` (built-in reporting) | HTML, XML |
| IntelliJ plugin (`:intellij-plugin`) | IDE only — no CLI output | — |

---

## 1. `structuredCoroutinesReport` — configuration report

The `structuredCoroutinesReport` task generates a **snapshot of the active plugin configuration**: which rules are enabled, at what severity, and which source sets or projects are excluded.

> **Note:** This report describes the _configuration_, not individual findings.
> To see the compiler-plugin findings, check the `compileKotlin` output in the CI log — each diagnostic includes the rule code, e.g. `[SCOPE_001]`.

### Running the task

```bash
./gradlew structuredCoroutinesReport
# Output:
#   build/reports/structured-coroutines/structured-coroutines-report.html
#   build/reports/structured-coroutines/structured-coroutines-report.txt
```

### Configuring the task

```kotlin
// build.gradle.kts
structuredCoroutines {
    reportFormat.set("html")           // "html", "text", or "all" (default)
    reportOutputDir.set(               // output directory (default: build/reports/structured-coroutines/)
        layout.buildDirectory.dir("reports/coroutines")
    )
}
```

---

## 2. GitHub Actions — full workflow

The workflow below integrates all three analysis layers and archives reports as build artifacts visible in the GitHub Actions UI.

```yaml
# .github/workflows/code-quality.yml
name: Code Quality

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main, develop]

jobs:
  structured-coroutines:
    name: Structured Coroutines Analysis
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3

      # ── 1. Compiler plugin ────────────────────────────────────────────────
      # compileKotlin applies the compiler-plugin rules automatically.
      # Use --continue so that Detekt and Lint still run even when there are errors.
      - name: Compile (compiler plugin rules)
        run: ./gradlew compileKotlin --continue
        continue-on-error: true   # collect all reports before marking the job as failed

      # Generate the active-configuration report
      - name: Generate Structured Coroutines config report
        run: ./gradlew structuredCoroutinesReport

      # ── 2. Detekt ────────────────────────────────────────────────────────
      - name: Run Detekt
        run: ./gradlew detekt --continue
        continue-on-error: true

      # ── 3. Android Lint (only for projects with Android modules) ─────────
      - name: Run Android Lint
        run: ./gradlew lint --continue
        continue-on-error: true

      # ── 4. Archive reports ───────────────────────────────────────────────
      - name: Archive analysis reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: structured-coroutines-reports
          path: |
            **/build/reports/structured-coroutines/
            **/build/reports/detekt/
            **/build/reports/lint-results*.html
          retention-days: 30
```

---

## 3. Build failure strategy

### Option A — use profiles

Choose a profile that matches your project's maturity:

```kotlin
// build.gradle.kts (new project — strict)
structuredCoroutines {
    useStrictProfile()   // rules set to ERROR block the build; WARNING rules allow it to continue
}

// build.gradle.kts (migration in progress — gradual)
structuredCoroutines {
    useGradualProfile()  // all rules set to WARNING; CI never fails due to these rules alone
}
```

With the `strict` profile (recommended for new projects), rules configured as `error` cause the `compileKotlin` step to fail. The GitHub Actions step uses `continue-on-error: true` so the rest of the pipeline keeps running and collects all reports before the job is marked as failed.

### Option B — `--continue` globally

For projects that have not yet enabled the compiler plugin but want to review all findings before deciding what to fix:

```bash
./gradlew compileKotlin detekt lint --continue
```

---

## 4. Post the report as a PR comment (optional)

GitHub Actions can publish the plain-text report as a comment on the pull request:

```yaml
      - name: Comment PR with Structured Coroutines report
        if: github.event_name == 'pull_request'
        uses: actions/github-script@v7
        with:
          script: |
            const fs = require('fs');
            const glob = require('@actions/glob');
            const pattern = '**/build/reports/structured-coroutines/structured-coroutines-report.txt';
            const globber = await glob.create(pattern);
            const files = await globber.glob();
            if (files.length === 0) return;
            const report = fs.readFileSync(files[0], 'utf8');
            await github.rest.issues.createComment({
              issue_number: context.issue.number,
              owner: context.repo.owner,
              repo: context.repo.repo,
              body: '### Structured Coroutines Configuration\n```\n' + report + '\n```'
            });
```

---

## 5. Detekt HTML report

Detekt generates its own reports under `build/reports/detekt/`. Enable the HTML, XML, and SARIF formats in your `detekt {}` block:

```kotlin
// build.gradle.kts
detekt {
    reports {
        html  { required.set(true) }
        xml   { required.set(true) }
        sarif { required.set(true) }
    }
}
```

The Detekt HTML report lists findings from all 19 Detekt rules in the toolkit — including `WithTimeoutScopeCancellation`, `ChannelNotClosed`, `FlowBlockingCall`, and others — with the rule code, description, and a link to the documentation.

---

## 6. SARIF and GitHub Code Scanning

Detekt can export findings in SARIF format, which GitHub displays directly in the **Security → Code scanning alerts** tab:

```yaml
      - name: Upload Detekt SARIF
        uses: github/codeql-action/upload-sarif@v3
        if: always()
        with:
          sarif_file: build/reports/detekt/detekt.sarif
          category: detekt
```

---

## 7. Quick-reference task table

| Gradle task | What it does |
|-------------|--------------|
| `compileKotlin` | Compiles with the active compiler plugin; errors/warnings appear in the log |
| `structuredCoroutinesReport` | Generates HTML + plain-text report of the active configuration |
| `detekt` | Runs all 19 Detekt rules with HTML / XML / SARIF output |
| `lint` | Runs all 21 Android Lint rules with HTML / XML output |
| `testAll` | Runs tests for all modules (excludes `:sample`, which fails by design) |

---

## 8. Excluding legacy modules

If some modules contain known violations not yet fixed, exclude them from the compiler plugin without affecting the rest of the project:

```kotlin
// legacy module — build.gradle.kts
structuredCoroutines {
    excludeProjects(":legacy-module", ":old-feature")
    // or exclude specific source sets:
    excludeSourceSets("legacyMain", "integrationTest")
}
```

This lets you enable the toolkit on all new modules while gradually migrating legacy code. See also [`docs/GRADUAL_ADOPTION.md`](GRADUAL_ADOPTION.md).
