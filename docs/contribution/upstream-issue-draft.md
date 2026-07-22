# New skill proposal: Kotlin Coroutines (+ new `concurrency` category)

> DRAFT ONLY. This file is a local planning artifact. It has not been posted, opened, or submitted
> as an issue or PR to `Kotlin/kotlin-agent-skills` or any other external repository. It exists to
> capture the content an eventual GitHub issue would contain, for maintainer review before
> submission.

## Problem / Use Case

Coding agents frequently write or review Kotlin coroutine code without a rigorous, rule-coded
reviewer for structured concurrency. In practice this shows up as recurring, easy-to-miss defects
that agents either introduce themselves or fail to catch in review:

- **`GlobalScope` leaks** — work launched outside any lifecycle-bound scope keeps running (and can
  leak) after the owning component (ViewModel, Activity, Presenter) is destroyed.
- **`runBlocking` inside `suspend` functions** — blocks the calling thread, breaking the
  non-blocking model and risking deadlocks or ANRs.
- **Missing `Flow` `.catch {}`** — an uncaught exception upstream in a `Flow` chain cancels the
  entire collecting scope (e.g. `viewModelScope`), not just the flow, producing hard-to-diagnose
  "unrelated feature stopped working" bugs.
- **Hardcoded or wrong `Dispatchers`** — untestable code (no dispatcher injection) or dispatcher
  misuse across platforms, e.g. `Dispatchers.IO` referenced from Kotlin Multiplatform `commonMain`
  where it does not exist on Native/JS targets.
- **Unhandled `withTimeout` cancellation** — `TimeoutCancellationException` is a
  `CancellationException` subclass; left uncaught it cancels the parent scope, not just the timed
  operation, silently killing sibling coroutines.

This skill (`kotlin-coroutines-skill`) codifies these and other structured-concurrency rules as a
triage-first playbook: a dispatch table maps symptoms/questions to a self-contained reference file
(Bad Practice → Recommended → Why → Quick fix), plus a hand-curated eval suite that exercises the
rules against representative, realistic Kotlin snippets — including two negative controls (an
already-correct snippet that must not be flagged, and an off-topic question that must not force
coroutine advice) so the skill can be judged on both recall and precision.

## Maintenance Commitment

The author (`github:@santimattius`) commits to keeping this skill current as Kotlin and
`kotlinx.coroutines` evolve. The skill's reference content is generator-backed: rule prose is
sourced from a single `docs/BEST_PRACTICES_COROUTINES.md`-style source of truth and mapped via
`ref-manifest.yml` / `ref-examples.yml` through `scripts/generate_refs.py`, so most content updates
are low-drift, mechanical regenerations rather than hand-edits scattered across dozens of files.
Hand-maintained reference files (outside the generator, e.g. the GlobalScope, runBlocking-in-suspend,
and withTimeout-cancellation references) are small in number and reviewed alongside any coroutine
API changes relevant to their rule.

## Dependency Risk Assessment

This skill is documentation and prompt content only — it ships no runtime dependency, no build
script, and no executable code path beyond the repository's own reference-generation tooling. Its
guidance tracks the public `kotlinx.coroutines` API surface (structured concurrency, `Flow`,
`Dispatchers`, cancellation) rather than any specific library version, so supply-chain risk is low:
there is nothing here to compromise at install time, and updates only ever change prose/examples,
never executable logic bundled with the skill itself.

## Proposed Category

**Primary candidate: `concurrency`**

Justification: structured concurrency in Kotlin coroutines is a cross-cutting runtime concern — it
touches UI (ViewModel/Activity scopes), backend services, and Kotlin Multiplatform shared code
alike. It does not fit either of the two existing categories: `backend` is too narrow (this skill
applies equally to Android/Compose and KMP code with no backend involved), and `tooling` implies
build/dev-tooling rather than a runtime-correctness reviewer. A dedicated `concurrency` category
also gives a natural home for future related skills (e.g. Java/JVM concurrency primitives, RxJava,
reactive streams, Kotlin Flow-specific deep dives) instead of forcing them into an unrelated bucket
as the skill set grows.

**Fallback candidate: `language`** — considered and rejected as too broad; it would absorb
unrelated Kotlin-syntax skills (e.g. sealed classes, delegation, contracts) that have nothing to do
with concurrency correctness, diluting the category's usefulness for discovery.

We ask maintainers to confirm (or propose an alternative) before any rename of the skill directory
or its `name` frontmatter field is made — that rename is intentionally deferred to a follow-up
change pending this confirmation.

## Evals Status

`evals/evals.json` is present with 10 hand-curated cases covering:
- 4 entries derived from generator-backed manifest rules (`TEST_005`, `FLOW_005`, `KMP_001`,
  `INTEROP_001`)
- 3 entries derived from hand-maintained reference files (GlobalScope misuse, `runBlocking` in
  suspend, `withTimeout` scope cancellation)
- 2 negative-control entries (one already-correct snippet that must not be flagged; one off-topic
  question that must not be redirected to coroutine advice)
- 1 informal-phrasing variant testing natural-language robustness on an existing rule

`metadata.tested_models.last_eval` in `SKILL.md` is intentionally set to `"TBD"` — it will be
filled in from a real eval run against the target agent/model before this issue or any follow-up PR
is opened, so the recorded evaluation date is never fabricated.
