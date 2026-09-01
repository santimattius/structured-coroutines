# sample-severity

A standalone, runnable demonstration of the `structuredCoroutines { }` severity DSL: how a
configured severity (`"error"` / `"warning"` / `"disabled"`) resolves to what actually reports at
compile time, and how *tightening* a rule (making it stricter) behaves differently from
*relaxing* it (making it looser) under the grace-period policy introduced in
[#68](https://github.com/santimattius/structured-coroutines/issues/68).

This module is **doc-only**: it is not wired into the root `testAll` task or CI, and it is not
included as a subproject of the root build (see "Why a standalone build" below). All four
behaviors it demonstrates are already covered by automated tests in
`compiler/src/test/kotlin/io/github/santimattius/structured/compiler/StructuredCoroutinesPluginFunctionalTest.kt`.
This module exists for human-readable, hands-on demonstration — not for regression coverage.

It complements [`sample/`](../sample/src/main/kotlin/io/github/santimattius/structured/sample/compilation/README.md)
(one example per compiler rule, at default severity) by showing what happens when you
**reconfigure** severity through the Gradle DSL instead.

## Why a standalone build

Unlike [`sample-detekt/`](../sample-detekt/README.md) — a subproject of the root build, because
`io.gitlab.arturbosch.detekt` resolves from `gradlePluginPortal()` — `io.github.santimattius.structured-coroutines`
only ever exists in `mavenLocal()`. Including a module that requires it as a root subproject
would make Gradle try to resolve it for *every* root task (not just this module's), breaking the
root build the moment the artifact isn't published yet. `sample-severity/` is therefore a fully
standalone Gradle build with its own `settings.gradle.kts`, invoked with `-p`:

```bash
./gradlew -p sample-severity compileKotlin
```

`-p sample-severity` makes this directory the build root for the invocation (so its own
`settings.gradle.kts` and `pluginManagement { }` block apply), while reusing the root project's
Gradle wrapper — no duplicate `gradlew`/`gradle/wrapper` is copied here. The root
`settings.gradle.kts` and root `build.gradle.kts` are untouched by this module's existence.

## Prerequisite

Publish the plugin artifacts to `mavenLocal()` first, from the repo root:

```bash
./gradlew publishToMavenLocal
```

`sample-severity/gradle.properties` pins `structuredCoroutinesVersion` to the plugin version this
publishes — it must match the root project's `PROJECT_VERSION`
(`structured-coroutines/gradle.properties`) exactly, verbatim, never invented.

## The DSL block

`sample-severity/build.gradle.kts` configures three rules in one block:

```kotlin
structuredCoroutines {
    globalScopeUsage.set("disabled")   // relaxation: immediate, policy-independent
    unusedDeferred.set("warning")      // relaxation: immediate, policy-independent
    loopWithoutYield.set("error")      // TIGHTENING: the only policy-sensitive line
    severityEnforcement.set(
        providers.gradleProperty("severityEnforcement").orElse("grace")
    )
}
```

Only `loopWithoutYield` reacts to the `severityEnforcement` toggle. The other two lines are
*relaxations* — they always apply immediately, regardless of policy, because a relaxation can only
make a previously-failing build pass, never break one.

## Command A — GRACE (default)

```bash
./gradlew -p sample-severity compileKotlin
```

Result: **BUILD SUCCESSFUL**.

| # | Scenario | Rule | Assertion |
|---|---|---|---|
| 1 | Suppression | `globalScopeUsage` (default `"error"`) → `"disabled"` | `[SCOPE_001]` is **absent** from the output |
| 2 | Immediate relaxation | `unusedDeferred` (default `"error"`) → `"warning"` | a line containing `[SCOPE_002]` whose trimmed start is `w:` |
| 3 | Deferred tightening | `loopWithoutYield` (default `"warning"`) → `"error"` | a line containing `[CANCEL_001]` whose trimmed start is `w:` (build does not fail on it) |
| 3b | Advisory | (same rule) | one additional warning naming the rule, the configured value, the currently-effective severity, and the enforcing release — see below |

Verified output (default log level — **no `--info` required**; the advisory renders as an
ordinary Gradle-lifecycle warning line):

```
w: Structured Coroutines: rule 'loopWithoutYield' is configured to "error" but keeps reporting as "warning" during the grace period. It will start enforcing "error" in version <ENFORCING_VERSION>. Set severityEnforcement = "strict" to enforce it now.
w: .../DisabledRuleExample.kt:19:5 This is a delicate API and its use requires care. ...
w: .../RelaxedRuleExample.kt:19:20 [SCOPE_002] async call creates a Deferred that is never awaited. ...
w: .../TightenedRuleExample.kt:22:5 [CANCEL_001] Loop in suspend function without cooperation point. ...

BUILD SUCCESSFUL
```

<!-- Update when SeverityGracePeriod.ENFORCING_VERSION changes -->
`<ENFORCING_VERSION>` above is `SeverityGracePeriod.ENFORCING_VERSION` — never copy the literal
version string into prose; only the one line above (elided) references it, flagged for update.

The `[SCOPE_001]`-suppressing rule never emits a "delicate API" style warning of its own from the
structured-coroutines plugin, but `GlobalScope.launch { }` is itself annotated `@DelicateCoroutinesApi`
by kotlinx.coroutines, so the Kotlin compiler's own delicate-API warning still appears — that
warning is unrelated to `structuredCoroutines { }` and is not a `[SCOPE_001]` line.

## Command B — STRICT

```bash
./gradlew -p sample-severity compileKotlin -PseverityEnforcement=strict
```

Result: **BUILD FAILED**.

| # | Scenario | Assertion |
|---|---|---|
| 4 | Immediate tightening | the line containing `[CANCEL_001]` trimmed-starts with `e:` |
| 4b | Advisory gone | `<ENFORCING_VERSION>` is **absent** — `deferredTightenings` is empty under STRICT |
| 4c | Relaxations still take effect | `[SCOPE_001]` remains absent — `globalScopeUsage` stays disabled regardless of policy |

Verified output:

```
e: .../TightenedRuleExample.kt:22:5 [CANCEL_001] Loop in suspend function without cooperation point. ...

BUILD FAILED
```

**Empirical note (verified, not assumed):** in this failed build, only the diagnostic that causes
the failure (`[CANCEL_001]` at `e:`) is printed to the console — even with `--info`. The
`[SCOPE_002]` warning line (still `"warning"` under this policy) does **not** appear in this run's
output, unlike Command A's successful build, where all diagnostics are shown. This is a real
difference between a successful and a failed `compileKotlin` invocation with this Kotlin/Gradle
toolchain (Kotlin 2.4.0's Build Tools API compilation path); it is not evidence that
`unusedDeferred` stopped being `"warning"` — it still is, and the parent project's functional
test suite verifies `[SCOPE_002]` at `"warning"` in isolation
(`StructuredCoroutinesPluginFunctionalTest.kt`). Do not rely on console output alone to prove a
non-erroring rule's severity in a build that already failed for another reason.

## Negative control (no new flag — proves the suppression, not just its absence)

Absence of `[SCOPE_001]` alone is not proof that suppression works — an always-silent plugin
would "pass" too. To prove it:

1. Comment out `globalScopeUsage.set("disabled")` in `sample-severity/build.gradle.kts`.
2. Re-run: `./gradlew -p sample-severity clean compileKotlin`.
3. Confirm the build now **FAILS** with a line containing `[SCOPE_001]` trimmed-starting `e:`
   (verified).
4. Restore the `globalScopeUsage.set("disabled")` line.

## Incremental-build note

Commands A and B differ in a compiler-plugin option (`severityEnforcement`), so switching between
them always re-runs `compileKotlin` (verified). Re-running the exact same command a second time
without changes shows `compileKotlin UP-TO-DATE` and prints no diagnostics (verified) — prefix
`clean` (e.g. `./gradlew -p sample-severity clean compileKotlin`) to see the diagnostics again.

## The `SeverityGracePeriod.ENFORCING_VERSION` flip caveat

The scenario table above describes **GRACE-default** behavior (`SeverityGracePeriod.DEFAULT_POLICY
= GRACE`, this release). At `SeverityGracePeriod.ENFORCING_VERSION`, `DEFAULT_POLICY` flips to
`STRICT`: Command A's scenario 3 will then also **fail** the build (converging with Command B's
scenario 4), and the grace-period advisory (scenario 3b) will no longer appear under Command A
because there is no longer anything left to defer. Re-verify this README's Command A output after
that flip ships.

## Rollback

This module is fully self-contained. `rm -rf sample-severity/` plus reverting the two
`sample-severity` rows in the root `README.md` fully restores a working repository — no other
file references this directory, and no root build file is modified by its existence.
