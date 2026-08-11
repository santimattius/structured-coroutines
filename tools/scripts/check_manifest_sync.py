#!/usr/bin/env python3
"""
Assert that the shared metadata fields declared across the plugin manifests never silently
drift: `.claude-plugin/plugin.json`, the matching entry in `.claude-plugin/marketplace.json`,
and (when present) `.codex-plugin/plugin.json`.

This is an assertion script, not a generator (unlike generate_refs.py) -- three hand-edited
JSON files justify a check, not a build step.

Compared fields: name, description, author (compared via `.name`), license, keywords (set
equality, order-insensitive).

Deliberately NOT compared, so a future contributor does not "fix" it:
  - `version` (package version, e.g. 1.1.0) vs `kotlin-coroutines-skill/SKILL.md`
    `metadata.version` (content version, e.g. 3.0.0) -- these are legitimately independent
    semantics (package release vs. skill content revision) and comparing them would produce
    permanent false positives.
  - `marketplace.json` top-level `version` -- catalog version, a different semantic again.
  - `repository` -- present in both `.claude-plugin/plugin.json` and
    `.codex-plugin/plugin.json`, but intentionally left out of the compared-field set for this
    guard (spec/design scope: name, description, author, license, keywords only).

If `.codex-plugin/plugin.json` is absent, the guard exits 0 with a printed skip notice --
Tier 2 (Codex packaging) is allowed to slip or be rolled back independently of Tier 3 (this
guard) without reddening CI.

Usage (from repo root):
  python3 tools/scripts/check_manifest_sync.py
"""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

TOOLS_DIR = Path(__file__).resolve().parent.parent
REPO_ROOT = TOOLS_DIR.parent

CLAUDE_PLUGIN = REPO_ROOT / ".claude-plugin" / "plugin.json"
MARKETPLACE = REPO_ROOT / ".claude-plugin" / "marketplace.json"
CODEX_PLUGIN = REPO_ROOT / ".codex-plugin" / "plugin.json"

# Fields compared across manifests. `version` is deliberately excluded -- see module docstring.
COMPARED_FIELDS = ("name", "description", "author", "license", "keywords")


def load_json(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as f:
        return json.load(f)


def normalize_author(value: Any) -> str | None:
    """Author may be `{"name": "..."}` (or, in principle, a bare string) -- compare on name."""
    if isinstance(value, dict):
        return value.get("name")
    return value


def normalize_field(field: str, value: Any) -> Any:
    if field == "author":
        return normalize_author(value)
    if field == "keywords":
        return set(value) if value is not None else None
    return value


def extract_fields(manifest: dict[str, Any]) -> dict[str, Any]:
    return {field: normalize_field(field, manifest.get(field)) for field in COMPARED_FIELDS}


def find_marketplace_entry(marketplace: dict[str, Any], name: str) -> dict[str, Any] | None:
    for plugin in marketplace.get("plugins", []):
        if plugin.get("name") == name:
            return plugin
    return None


def diff_fields(
    reference_label: str,
    reference: dict[str, Any],
    other_label: str,
    other: dict[str, Any],
) -> list[str]:
    """Return human-readable diff lines for fields that disagree between two manifests."""
    mismatches: list[str] = []
    for field in COMPARED_FIELDS:
        ref_value = reference.get(field)
        other_value = other.get(field)
        if ref_value != other_value:
            mismatches.append(
                f"  - field `{field}` differs: {reference_label}={ref_value!r} "
                f"vs {other_label}={other_value!r} (in {other_label})"
            )
    return mismatches


def main() -> int:
    if not CLAUDE_PLUGIN.is_file():
        print(f"ERROR: required manifest not found: {CLAUDE_PLUGIN}", file=sys.stderr)
        return 1
    if not MARKETPLACE.is_file():
        print(f"ERROR: required manifest not found: {MARKETPLACE}", file=sys.stderr)
        return 1

    claude_plugin = load_json(CLAUDE_PLUGIN)
    marketplace = load_json(MARKETPLACE)

    plugin_name = claude_plugin.get("name")
    marketplace_entry = find_marketplace_entry(marketplace, plugin_name)
    if marketplace_entry is None:
        print(
            f"ERROR: no marketplace.json plugins[] entry with name == {plugin_name!r} "
            f"found in {MARKETPLACE}",
            file=sys.stderr,
        )
        return 1

    claude_fields = extract_fields(claude_plugin)
    marketplace_fields = extract_fields(marketplace_entry)

    all_mismatches: list[str] = []
    all_mismatches += diff_fields(
        ".claude-plugin/plugin.json", claude_fields, ".claude-plugin/marketplace.json",
        marketplace_fields,
    )

    if not CODEX_PLUGIN.is_file():
        print(
            f"SKIP: {CODEX_PLUGIN} not found -- Codex packaging (Tier 2) is optional; "
            "sync check limited to Claude manifests.",
        )
    else:
        codex_plugin = load_json(CODEX_PLUGIN)
        codex_fields = extract_fields(codex_plugin)
        all_mismatches += diff_fields(
            ".claude-plugin/plugin.json", claude_fields, ".codex-plugin/plugin.json",
            codex_fields,
        )

    if all_mismatches:
        print("Manifest sync check FAILED. Shared fields diverged:", file=sys.stderr)
        for line in all_mismatches:
            print(line, file=sys.stderr)
        return 1

    print("Manifest sync check passed: name/description/author/license/keywords agree.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
