# Migration: v0.7.x → v1.0.0

## 1. Bump dependencies

Align all Structured Coroutines artifacts to **1.0.0** and **kotlinx-coroutines 1.11.0**.

## 2. Adopt profiles incrementally

| Step | Profile | Adds |
|------|---------|------|
| 1 | `useGradualProfile()` | Baseline warnings |
| 2 | `useStrictProfile()` | Compiler errors on critical rules |
| 3 | `useAndroidComposeProfile()` / `useKmpCommonProfile()` | INTEROP_001/002 as errors |
| 4 | `useKtorBackendProfile()` / `useSpringBackendProfile()` | Backend + concurrency pack |

## 3. New rule waves

- **v0.8:** INTEROP, FLOW_005/010, CONCUR_003, TEST_004, COMPOSE_001, KMP_001
- **v0.9:** CONCUR_001/002/004, FLOW_006–008, KMP_002/003, BACKEND_001/002
- **v1.0:** COMPOSE_002/003, TEST_005/006, FLOW_009/011, INTEROP_003/004, DEBUG_001 (opt-in)

Use [rule-codes.yml](rule-codes.yml) and [SUPPRESSING_RULES.md](SUPPRESSING_RULES.md) for suppression IDs.

## 4. KMP projects

Replace `Dispatchers.IO` in `commonMain` with injected dispatchers (`@IoDispatcher` in `:annotations`).

## 5. Compose

Replace `collectAsState()` with `collectAsStateWithLifecycle()` on Android (`COMPOSE_001`).

## 6. Baseline (large codebases)

```kotlin
structuredCoroutines {
    baselineFile.set(rootProject.file("coroutines-baseline.xml"))
    baselineEnabled.set(true)
}
```

Run `./gradlew generateCoroutinesBaseline` once, then fix **new** violations only.
