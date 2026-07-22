#!/usr/bin/env python3
"""Run kotlin-coroutines-skill/evals/evals.json against a live agent client.

For each eval case, runs the prompt twice with a clean context: once with the
skill enabled (with_skill) and once with it disabled (without_skill). Output
layout follows the agentskills.io eval workflow:

    <workspace>/iteration-N/eval-<slug>/with_skill/outputs/response.md
    <workspace>/iteration-N/eval-<slug>/with_skill/timing.json
    <workspace>/iteration-N/eval-<slug>/with_skill/raw.json
    <workspace>/iteration-N/eval-<slug>/without_skill/...

Grading against each case's `assertions` is a separate, later step (manual or
LLM-judge) — this script only runs and captures, it does not grade.

Supported clients:
  claude  -- verified against Claude Code CLI 2.1.217 on this machine.
  codex   -- BEST-EFFORT / UNVERIFIED. `codex` was not installed on the
             machine this script was written on, so the exact CLI flags
             could not be confirmed. Run `codex exec --help` yourself and
             adjust CODEX_CMD below if flags differ.
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import time
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
SKILL_DIR = REPO_ROOT / "kotlin-coroutines-skill"
EVALS_JSON = SKILL_DIR / "evals" / "evals.json"
DEFAULT_WORKSPACE = REPO_ROOT.parent / "kotlin-coroutines-skill-workspace"

# The plugin id as registered in `.claude-plugin/marketplace.json` /
# global settings.json -> enabledPlugins. Verified by inspecting
# ~/.claude/settings.json on the machine this script was written on.
CLAUDE_PLUGIN_ID = "kotlin-coroutines-skill@structured-coroutines"


def load_evals() -> list[dict]:
    data = json.loads(EVALS_JSON.read_text())
    return data["evals"]


def neutral_cwd(workspace: Path) -> Path:
    """A directory outside the repo, so a client's file-reading tools can't
    stumble onto the skill's own reference files and contaminate the
    without_skill baseline. Verified necessary: running the baseline from
    inside this repo let the model read ref-1-1-global-scope.md on its own
    and reproduce skill-flavored advice even with the plugin disabled."""
    d = workspace / "_neutral_cwd"
    d.mkdir(parents=True, exist_ok=True)
    return d


# ---------------------------------------------------------------------------
# Claude Code runner (verified: claude 2.1.217, output-format json)
# ---------------------------------------------------------------------------

def run_claude(prompt: str, with_skill: bool, model: str, cwd: Path) -> dict:
    cmd = [
        "claude",
        "-p",
        prompt,
        "--output-format",
        "json",
        "--no-session-persistence",
        "--model",
        model,
    ]
    if not with_skill:
        # Surgically disable only this project's plugin for one invocation.
        # (--disable-slash-commands was tested and did NOT reliably suppress
        # the skill's influence; this --settings override does.)
        cmd += ["--settings", json.dumps({"enabledPlugins": {CLAUDE_PLUGIN_ID: False}})]

    proc = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, timeout=300)
    if proc.returncode != 0:
        raise RuntimeError(f"claude exited {proc.returncode}: {proc.stderr[:2000]}")

    raw = json.loads(proc.stdout)
    usage = raw.get("usage", {})
    return {
        "raw": raw,
        "result_text": raw.get("result", ""),
        "timing": {
            "total_tokens": usage.get("input_tokens", 0) + usage.get("output_tokens", 0),
            "duration_ms": raw.get("duration_ms"),
            "cost_usd": raw.get("total_cost_usd"),
        },
    }


# ---------------------------------------------------------------------------
# Codex CLI runner -- UNVERIFIED, codex was not installed when this was written.
# Based on the `codex exec --json` pattern described in OpenAI's eval-skills
# writeup (developers.openai.com/blog/eval-skills), which captures a JSONL
# event stream. Re-check `codex exec --help` before trusting this.
# ---------------------------------------------------------------------------

def run_codex(prompt: str, with_skill: bool, model: str, cwd: Path) -> dict:
    cmd = ["codex", "exec", "--json"]
    if model:
        cmd += ["--model", model]
    if not with_skill:
        # UNVERIFIED: codex was not available to confirm a per-run flag for
        # disabling project-registered skills/plugins. If codex reads an
        # AGENTS.md / skill directory automatically the way Claude Code
        # reads CLAUDE.md + enabledPlugins, the equivalent knob may be a
        # `--no-project-doc` / `--skip-git-repo-check`-style flag, or may
        # require literally running from a directory with no skill config.
        # Confirm with `codex exec --help` and fix this branch before relying
        # on it for real without_skill numbers.
        pass
    cmd += [prompt]

    proc = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, timeout=300)
    if proc.returncode != 0:
        raise RuntimeError(f"codex exited {proc.returncode}: {proc.stderr[:2000]}")

    # codex exec --json streams one JSON object per line (JSONL). The final
    # assistant message and token/duration totals are assumed to be on the
    # last "type": "message"/"result"-ish line -- UNVERIFIED shape, adjust
    # once you can inspect real output.
    events = [json.loads(line) for line in proc.stdout.splitlines() if line.strip()]
    last = events[-1] if events else {}
    result_text = last.get("result") or last.get("content") or json.dumps(last)
    return {
        "raw": {"events": events},
        "result_text": result_text,
        "timing": {
            "total_tokens": last.get("total_tokens"),
            "duration_ms": last.get("duration_ms"),
            "cost_usd": None,
        },
    }


RUNNERS = {"claude": run_claude, "codex": run_codex}


def run_case(client: str, case: dict, model: str, iteration_dir: Path, cwd: Path) -> None:
    slug = case.get("slug") or f"eval-{case['id']}"
    case_dir = iteration_dir / f"eval-{slug}"

    for label, with_skill in (("with_skill", True), ("without_skill", False)):
        out_dir = case_dir / label
        outputs_dir = out_dir / "outputs"
        marker = out_dir / "timing.json"
        if marker.exists():
            print(f"  [skip] {slug}/{label} already done")
            continue

        outputs_dir.mkdir(parents=True, exist_ok=True)
        print(f"  [run]  {slug}/{label} ({client}, model={model})")
        result = RUNNERS[client](case["prompt"], with_skill, model, cwd)

        (outputs_dir / "response.md").write_text(result["result_text"])
        (out_dir / "raw.json").write_text(json.dumps(result["raw"], indent=2))
        (out_dir / "timing.json").write_text(json.dumps(result["timing"], indent=2))


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--client", choices=["claude", "codex"], default="claude")
    ap.add_argument("--model", default="sonnet", help="Model alias/name to pass to the client CLI")
    ap.add_argument("--workspace", type=Path, default=DEFAULT_WORKSPACE)
    ap.add_argument("--iteration", type=int, default=1)
    ap.add_argument("--limit", type=int, default=None, help="Only run the first N cases (useful for a smoke test)")
    ap.add_argument("--ids", type=str, default=None, help="Comma-separated eval ids to run, e.g. 1,5,28")
    args = ap.parse_args()

    if args.client == "codex":
        print(
            "WARNING: the codex runner is unverified (codex CLI was not installed "
            "when this script was written). Expect to fix flags/JSON parsing.",
            file=sys.stderr,
        )

    evals = load_evals()
    if args.ids:
        wanted = {int(x) for x in args.ids.split(",")}
        evals = [e for e in evals if e["id"] in wanted]
    if args.limit:
        evals = evals[: args.limit]

    iteration_dir = args.workspace / f"iteration-{args.iteration}"
    cwd = neutral_cwd(args.workspace)

    print(f"Running {len(evals)} case(s) x2 (with/without skill) via {args.client}, model={args.model}")
    print(f"Workspace: {iteration_dir}")
    print(f"Neutral cwd (isolates baseline from repo files): {cwd}")

    start = time.time()
    for i, case in enumerate(evals, 1):
        print(f"[{i}/{len(evals)}] id={case['id']} slug={case.get('slug')}")
        run_case(args.client, case, args.model, iteration_dir, cwd)

    print(f"Done in {time.time() - start:.1f}s. Next step: grade outputs against each case's assertions.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
