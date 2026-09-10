# Kotlin 2.4.20 Bump — Unblock Checklist

Tracked thresholds that must all be met before the production `kotlin` catalog
ref in `gradle/libs.versions.toml` can move to Kotlin 2.4.20+ (targeted for
toolkit v2.0.0). See the Engram decision
`decision/project-decision-kotlin-2-4-20-bump-targeted-for` (obs #1244) for
why the bump is deferred rather than applied now, and
[KOTLIN_2_4_FIR_API_AUDIT.md](KOTLIN_2_4_FIR_API_AUDIT.md) for the checker-level
readiness findings this change also produced.

A future bump proposal should be able to answer "is the bump viable yet?"
directly from this table, without re-research.

| Tool | Current version (this repo) | Required version | Source / evidence | Status |
|------|------------------------------|-------------------|--------------------|--------|
| detekt | `1.23.7` (`gradle/libs.versions.toml` → `detekt`); latest 2.x line is alpha-only (`2.0.0-alpha.6`) | `2.0.0` GA (first stable release with Kotlin 2.4.x compiler support) | [detekt.dev compatibility table](https://detekt.dev/docs/introduction/compatibility) and [detekt/detekt#8865](https://github.com/detekt/detekt/issues/8865) | Blocked — no stable detekt release supports Kotlin 2.4.x yet |
| Android Lint / AGP | `lint-api` `31.4.0` (`gradle/libs.versions.toml` → `lint`; this repo has no direct AGP dependency — `lint-api` 31.4.0's own POM pairs it with AGP `8.4.0`, per its published `builder-model` dependency) | AGP `>= 8.5.2`, `lint-api` `>= ~31.5.2` | Android's official [AGP ↔ Lint API / Kotlin support table](https://developer.android.com/studio/releases/gradle-plugin) and `lint-api` `31.4.0`'s Maven POM | Blocked — installed `lint-api` major predates the required Kotlin-support line |
| intellij-platform-gradle-plugin | `2.11.0` (`gradle/libs.versions.toml` → `intellij-platform`); plugin `2.12.0`'s `PlatformKotlinVersions` table still pins IDE build `2026.1` to Kotlin `2.3.10` | A `intellij-platform-gradle-plugin` release whose `PlatformKotlinVersions` table maps the project's target IDE build (`intellij-ide = 2026.1`) to Kotlin `>= 2.4.20` | `PlatformKotlinVersions` table shipped with the `intellij-platform-gradle-plugin` artifact (IntelliJ Platform Gradle Plugin docs) | Blocked — target IDE build `2026.1` is not yet mapped to Kotlin 2.4.x by any released plugin version |

## How to read an entry

Each row is self-sufficient: current version installed in this repo, the
version threshold that must be reached upstream, where that threshold is
verified, and whether it is currently blocking the bump. When a threshold is
met, update that row's **Status** to `Unblocked` with a refreshed evidence
link and date, rather than deleting the row — the table's value is in
tracking the full three-way gate over time.

## Additional pre-bump work surfaced by this readiness change

Not a blocking threshold on its own, but tracked here so it is not
rediscovered from scratch at bump time:

- `LoopWithoutYieldChecker.kt` and `ScoroutinesCallCheckerExtension.kt` use
  `FirSimpleFunctionChecker`, which is unresolved against Kotlin 2.4.20 in the
  `forwardCompatTest` smoke-compile task. See
  [KOTLIN_2_4_FIR_API_AUDIT.md](KOTLIN_2_4_FIR_API_AUDIT.md) finding #6/#9.
- `DispatchersUnconfinedChecker.kt`, `SuspendInFinallyChecker.kt`, and
  `UnstructuredLaunchChecker.kt` need the fix from the unmerged
  `fix/firresolvedqualifier-classid-crash-90` branch (issue #90) merged before
  or as part of the bump.

## Non-goals

This checklist does not upgrade detekt, Android Lint/AGP, or
intellij-platform-gradle-plugin, and does not bump the production `kotlin`
catalog ref. It exists purely to make the eventual bump decision fast and
evidence-based.
