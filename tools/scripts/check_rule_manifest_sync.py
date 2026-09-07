#!/usr/bin/env python3
"""
Assert that `docs/rule-codes.yml`'s per-surface coverage arrays (`compiler:`, `detekt:`,
`lint:`, `intellij:`) never silently drift from the 4 real rule registries:

  - compiler: the `ScoroutinesRule` enum
    (compiler/src/main/kotlin/io/github/santimattius/structured/compiler/ScoroutinesRule.kt)
  - detekt:   `StructuredCoroutinesRuleSetProvider`'s registration list, resolved to each
    registered `*Rule.kt`'s `id = "..."` (the provider registers classes; the manifest lists
    issue IDs, so this is a two-step lookup)
  - lint:     `StructuredCoroutinesIssueRegistry`'s registration list, resolved the same way to
    each registered `*Detector.kt`'s `id = "..."`
  - intellij: `plugin.xml`'s `shortName="..."` attributes on `<localInspection>` entries

This is an assertion script, not a generator (mirrors `check_manifest_sync.py`) -- rule
identifiers are hand-maintained in `rule-codes.yml`, so a check is what catches drift here, not
a build step that would regenerate the manifest's semantic content (title/section/doc_anchor).

Checks, in both directions:
  (a) every ID listed under a rule code's per-surface array actually exists in that surface's
      real registry (catches a manifest claiming coverage that was never implemented, renamed,
      or removed -- this exact drift class under-reported CANCEL_001/ARCH_002 coverage; see #80);
  (b) every ID actually registered in a surface appears in at least one manifest entry for that
      surface, unless listed in the `UNMAPPED` allowlist below with a documented reason (catches
      an implemented rule the manifest never mentions). "At least one", not "exactly one": some
      rules (e.g. `JobInBuilderContext`) intentionally back two distinct doc sections/rule codes
      (DISPATCH_004 "Passing Job() Directly" and EXCEPT_001 "SupervisorJob in Single Builder"),
      so a stricter "exactly one" would falsely flag that legitimate one-to-many mapping;
  (c) no duplicate `code:` values in the manifest.

Deliberately NOT parsed with a Kotlin/XML parser: these are regex/text extractions over a known,
narrow declaration shape (see the four `extract_*_ids` functions below) -- the same proportionate
text-extraction approach `check_manifest_sync.py`'s module docstring describes for its own three
hand-edited JSON files. This reads repo-local files and diffs sets in-process; no shelling out,
unlike `generate_refs.py`'s optional Ruby-psych fallback (deliberately not reused here).

Usage (from repo root):
  python3 tools/scripts/check_rule_manifest_sync.py
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

try:
    import yaml
except ImportError:
    print(
        "ERROR: PyYAML is required to run this script. Install it with `pip install pyyaml`.",
        file=sys.stderr,
    )
    sys.exit(1)

TOOLS_DIR = Path(__file__).resolve().parent.parent
REPO_ROOT = TOOLS_DIR.parent

RULE_CODES_YML = REPO_ROOT / "docs" / "rule-codes.yml"

COMPILER_ENUM = (
    REPO_ROOT
    / "compiler/src/main/kotlin/io/github/santimattius/structured/compiler/ScoroutinesRule.kt"
)
DETEKT_PROVIDER = (
    REPO_ROOT
    / "detekt-rules/src/main/kotlin/io/github/santimattius/structured/detekt"
    / "StructuredCoroutinesRuleSetProvider.kt"
)
DETEKT_RULES_DIR = (
    REPO_ROOT
    / "detekt-rules/src/main/kotlin/io/github/santimattius/structured/detekt/rules"
)
LINT_REGISTRY = (
    REPO_ROOT
    / "lint-rules/src/main/kotlin/io/github/santimattius/structured/lint"
    / "StructuredCoroutinesIssueRegistry.kt"
)
LINT_DETECTORS_DIR = (
    REPO_ROOT
    / "lint-rules/src/main/kotlin/io/github/santimattius/structured/lint/detectors"
)
INTELLIJ_PLUGIN_XML = (
    REPO_ROOT / "intellij-plugin/src/main/resources/META-INF/plugin.xml"
)

SURFACES = ("compiler", "detekt", "lint", "intellij")

# Registered IDs that are intentionally absent from every `rule-codes.yml` entry for a surface,
# with a one-line reason. Checked as part of direction (b). Empty today: every currently
# registered rule/detector/inspection maps into at least one manifest entry.
UNMAPPED: dict[str, set[str]] = {
    "compiler": set(),
    "detekt": set(),
    "lint": set(),
    "intellij": set(),
}


def read_text(path: Path) -> str:
    if not path.is_file():
        print(f"ERROR: required source file not found: {path}", file=sys.stderr)
        sys.exit(1)
    return path.read_text(encoding="utf-8")


def extract_compiler_ids() -> set[str]:
    """`ScoroutinesRule` enum constant names, e.g. `    GLOBAL_SCOPE_USAGE("...", ...),`."""
    text = read_text(COMPILER_ENUM)
    return set(re.findall(r'^\s{4}([A-Z][A-Z0-9_]*)\("', text, re.MULTILINE))


def resolve_ids_from_classes(class_names: set[str], classes_dir: Path) -> set[str]:
    """For each registered `*Rule`/`*Detector` class, read its file and extract `id = "..."`."""
    ids: set[str] = set()
    for class_name in class_names:
        class_file = classes_dir / f"{class_name}.kt"
        text = read_text(class_file)
        match = re.search(r'^\s*id = "([^"]+)"', text, re.MULTILINE)
        if match is None:
            print(
                f"ERROR: could not find `id = \"...\"` in {class_file} "
                f"(registered as {class_name})",
                file=sys.stderr,
            )
            sys.exit(1)
        ids.add(match.group(1))
    return ids


def extract_detekt_ids() -> set[str]:
    """Registered `*Rule` classes in `StructuredCoroutinesRuleSetProvider`'s `listOf(...)`,
    resolved to each rule's `id = "..."`."""
    text = read_text(DETEKT_PROVIDER)
    class_names = set(re.findall(r"(\w+Rule)\(config\)", text))
    return resolve_ids_from_classes(class_names, DETEKT_RULES_DIR)


def extract_lint_ids() -> set[str]:
    """Registered `*Detector` classes in `StructuredCoroutinesIssueRegistry`'s `listOf(...)`,
    resolved to each detector's `id = "..."`."""
    text = read_text(LINT_REGISTRY)
    class_names = set(re.findall(r"(\w+Detector)\.ISSUE", text))
    return resolve_ids_from_classes(class_names, LINT_DETECTORS_DIR)


def extract_intellij_ids() -> set[str]:
    """`shortName="..."` attributes on `<localInspection>` entries in `plugin.xml`."""
    text = read_text(INTELLIJ_PLUGIN_XML)
    return set(re.findall(r'shortName="([^"]+)"', text))


EXTRACTORS = {
    "compiler": extract_compiler_ids,
    "detekt": extract_detekt_ids,
    "lint": extract_lint_ids,
    "intellij": extract_intellij_ids,
}


def load_manifest_rules() -> list[dict]:
    with RULE_CODES_YML.open(encoding="utf-8") as f:
        manifest = yaml.safe_load(f)
    rules = manifest.get("rules")
    if not rules:
        print(f"ERROR: no `rules:` list found in {RULE_CODES_YML}", file=sys.stderr)
        sys.exit(1)
    return rules


def main() -> int:
    rules = load_manifest_rules()
    registries = {surface: EXTRACTORS[surface]() for surface in SURFACES}

    mismatches: list[str] = []

    # (c) no duplicate `code:` values.
    seen_codes: dict[str, int] = {}
    for rule in rules:
        code = rule.get("code")
        seen_codes[code] = seen_codes.get(code, 0) + 1
    for code, count in seen_codes.items():
        if count > 1:
            mismatches.append(f"DUPLICATE_CODE / manifest / {code} appears {count} times")

    # (a) every manifest ID exists in that surface's real registry.
    manifest_ids_by_surface: dict[str, set[str]] = {surface: set() for surface in SURFACES}
    for rule in rules:
        code = rule.get("code")
        for surface in SURFACES:
            ids = rule.get(surface) or []
            for rule_id in ids:
                manifest_ids_by_surface[surface].add(rule_id)
                if rule_id not in registries[surface]:
                    mismatches.append(
                        f"{code} / {surface} / unknown / manifest lists `{rule_id}` "
                        f"but it is not registered in the {surface} surface"
                    )

    # (b) every registered ID appears in at least one manifest entry for that surface, unless
    # explicitly allowlisted in UNMAPPED.
    for surface in SURFACES:
        missing = registries[surface] - manifest_ids_by_surface[surface] - UNMAPPED[surface]
        for rule_id in sorted(missing):
            mismatches.append(
                f"(none) / {surface} / missing / `{rule_id}` is registered in the {surface} "
                "surface but is not listed under any rule code in docs/rule-codes.yml"
            )

    if mismatches:
        print("Rule manifest sync check FAILED. Coverage diverged:", file=sys.stderr)
        for line in sorted(mismatches):
            print(f"  - {line}", file=sys.stderr)
        return 1

    print(
        "Rule manifest sync check passed: compiler/detekt/lint/intellij coverage arrays in "
        "docs/rule-codes.yml agree with the 4 real registries."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
