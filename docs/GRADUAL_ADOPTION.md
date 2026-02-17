# Gradual adoption guide

This guide helps you adopt the Structured Coroutines plugin in an existing codebase without breaking the build. It covers **profiles** (relaxed → gradual → strict), **excluding** source sets or projects, and **suppression** best practices.

**Related:** [gradle-plugin README](../gradle-plugin/README.md) (configuration), [SUPPRESSING_RULES.md](SUPPRESSING_RULES.md) (suppression IDs).

---

## 1. Step-by-step path: Relaxed → Gradual → Strict

| Step | Profile | Goal |
|------|---------|------|
| **1. Relaxed / Gradual** | `useGradualProfile()` or `useRelaxedProfile()` | Enable the plugin with **all rules as warnings**. Build succeeds; you see findings in IDE and CI. |
| **2. Fix and suppress** | Same | Fix violations where possible; use `@Suppress` for justified exceptions (see [Suppression best practices](#3-suppression-best-practices)). |
| **3. Strict** | `useStrictProfile()` | Once the codebase is clean (or only documented exceptions remain), switch to strict so new violations fail the build. |

**Example: enable without breaking the build**

```kotlin
// build.gradle.kts (root or module)
plugins {
    kotlin("jvm") version "2.3.0"
    id("io.github.santimattius.structured-coroutines") version "0.1.0"
}

structuredCoroutines {
    useGradualProfile()  // All warnings; no build failure
}
```

**Example: move to strict when ready**

```kotlin
structuredCoroutines {
    useStrictProfile()   // 7 rules error, 4 warning; violations block the build
}
```

---

## 2. Excluding legacy code

Use exclusions when you cannot fix or suppress in a given area yet, so the plugin does not run there.

### Exclude source sets

Disable the plugin for specific compilations (e.g. a legacy source set or tests):

```kotlin
structuredCoroutines {
    useGradualProfile()
    excludeSourceSets("legacyMain", "test")
}
```

Names match Kotlin compilation names: `main`, `test`, `jvmMain`, `commonMain`, or custom names.

### Exclude entire projects

Disable the plugin for whole subprojects:

```kotlin
// Root build.gradle.kts
structuredCoroutines {
    excludeProjects(":legacy-app", ":experimental")
}
```

Use Gradle project paths (e.g. `:subproject`, `:app:feature`).

### When to exclude

- **Temporary:** Legacy module that you will refactor later; exclude until you can run the plugin there.
- **Permanent:** Optional; prefer fixing or suppressing so the whole codebase is under the same rules. Document why a module is excluded (e.g. in README or ADR).

---

## 3. Suppression best practices

When you have a **justified exception** (e.g. deliberate `GlobalScope` for a process that must outlive the app), suppress the rule locally instead of disabling it globally.

### Do

- **Suppress at the narrowest scope:** function or file, not whole module.
- **Document why:** add a short comment next to `@Suppress` explaining the exception.
- **Use the correct ID per tool:** see [SUPPRESSING_RULES.md](SUPPRESSING_RULES.md) (Compiler vs Detekt vs Lint use different names for the same rule).

```kotlin
// Deliberate: fire-and-forget analytics that must survive activity lifecycle.
@Suppress("GLOBAL_SCOPE_USAGE")
fun sendAnalyticsEvent(event: AnalyticsEvent) {
    GlobalScope.launch(Dispatchers.IO) { api.post(event) }
}
```

### Avoid

- **Blanket suppression:** e.g. `@Suppress` on a whole file or package without fixing or documenting.
- **Suppressing multiple rules "just in case":** only suppress the rule that is actually reported.
- **Disabling the plugin for the whole project** when exclusions or suppressions would be enough.

### Reference

- **Rule codes and practices:** [BEST_PRACTICES_COROUTINES.md](BEST_PRACTICES_COROUTINES.md#rule-codes-reference)
- **Suppression IDs by tool:** [SUPPRESSING_RULES.md](SUPPRESSING_RULES.md)

---

## 4. Checklist for migration

- [ ] Apply the plugin with `useGradualProfile()` (or `useRelaxedProfile()`).
- [ ] Optionally exclude legacy modules or source sets with `excludeSourceSets` / `excludeProjects`.
- [ ] Run the build and fix or suppress reported violations; document suppressions.
- [ ] Align Detekt / Android Lint with the same rules and severities if you use them.
- [ ] When the codebase is ready, switch to `useStrictProfile()` so new violations fail the build.
