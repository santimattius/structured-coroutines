#!/usr/bin/env python3
"""Grade run_evals.py output against each case's assertions and compute
skill metrics: pass-rate with vs. without the skill, plus token/cost/
duration deltas.

Independence from the agent/session that generated the response (by
construction, not just by flag):
  - Grading happens in a separate process, run after run_evals.py has
    finished and exited -- it only reads response.md/timing.json from disk.
  - Each grading call is its own fresh `claude -p --no-session-persistence`
    invocation with no conversation history; the judge never sees the
    transcript that produced the response, only the final text.
  - The judge always runs with the skill itself disabled (--settings
    enabledPlugins override, same mechanism verified in run_evals.py), so
    skill-flavored phrasing can't bias the judge's own reasoning.
  - Default judge model is haiku, distinct from the sonnet/opus models
    typically used to generate responses, matching the separation-of-duties
    convention `claude plugin eval` uses (--judge-model, default: haiku).

Usage:
    python3 grade_evals.py --workspace ../kotlin-coroutines-skill-workspace --iteration 1
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
EVALS_JSON = REPO_ROOT / "tools" / "evals" / "evals.json"
DEFAULT_WORKSPACE = REPO_ROOT.parent / "kotlin-coroutines-skill-workspace"
CLAUDE_PLUGIN_ID = "kotlin-coroutines-skill@structured-coroutines"

JUDGE_PROMPT_TEMPLATE = """You are grading an AI assistant's response to a code-review request. \
You did not write this response and have no memory of producing it -- judge it purely on the \
text below against the checklist.

## Assertions to check (each must be independently true or false)
{assertions}

## Assistant's response being graded
---
{response}
---

For each assertion, decide if the response satisfies it. Respond with ONLY a JSON object, no \
markdown fences, no prose before or after, in exactly this shape:

{{"assertions": [{{"text": "<assertion text>", "met": true|false, "evidence": "<one short quote \
or paraphrase from the response, or 'not addressed'>"}}]}}
"""


def load_evals() -> list[dict]:
    return json.loads(EVALS_JSON.read_text())["evals"]


def load_install_info(workspace: Path) -> dict | None:
    info_path = workspace / "install-info.json"
    return json.loads(info_path.read_text()) if info_path.exists() else None


def extract_json(text: str) -> dict:
    text = text.strip()
    if text.startswith("```"):
        text = re.sub(r"^```(json)?\s*|\s*```$", "", text.strip())
    return json.loads(text)


def judge(assertions: list[str], response_text: str, model: str, plugin_id: str) -> dict:
    prompt = JUDGE_PROMPT_TEMPLATE.format(
        assertions="\n".join(f"- {a}" for a in assertions),
        response=response_text or "(empty response)",
    )
    cmd = [
        "claude",
        "-p",
        prompt,
        "--output-format",
        "json",
        "--no-session-persistence",
        "--model",
        model,
        "--settings",
        json.dumps({"enabledPlugins": {plugin_id: False}}),
    ]
    proc = subprocess.run(cmd, capture_output=True, text=True, timeout=180)
    if proc.returncode != 0:
        raise RuntimeError(f"judge call exited {proc.returncode}: {proc.stderr[:2000]}")
    raw = json.loads(proc.stdout)
    result_text = raw.get("result", "")
    try:
        parsed = extract_json(result_text)
    except json.JSONDecodeError as e:
        return {"error": f"unparseable judge output: {e}", "raw_result": result_text, "assertions": []}
    parsed["judge_usage"] = raw.get("usage", {})
    return parsed


def grade_case(case: dict, case_dir: Path, model: str, plugin_id: str) -> dict | None:
    grade_path = case_dir / "grade.json"
    if grade_path.exists():
        return json.loads(grade_path.read_text())

    result = {"id": case["id"], "slug": case.get("slug"), "conditions": {}}
    for label in ("with_skill", "without_skill"):
        response_path = case_dir / label / "outputs" / "response.md"
        timing_path = case_dir / label / "timing.json"
        if not response_path.exists():
            print(f"  [skip] {case_dir.name}/{label} -- no response.md yet, run run_evals.py first")
            return None

        response_text = response_path.read_text()
        timing = json.loads(timing_path.read_text()) if timing_path.exists() else {}
        verdict = judge(case["assertions"], response_text, model, plugin_id)
        assertions = verdict.get("assertions", [])
        met_count = sum(1 for a in assertions if a.get("met"))
        total = len(case["assertions"])
        result["conditions"][label] = {
            "pass_rate": (met_count / total) if total else 0.0,
            "met": met_count,
            "total": total,
            "assertions": assertions,
            "timing": timing,
            "judge_error": verdict.get("error"),
        }

    grade_path.write_text(json.dumps(result, indent=2))
    return result


def aggregate(results: list[dict]) -> dict:
    def avg(vals: list[float]) -> float | None:
        vals = [v for v in vals if v is not None]
        return sum(vals) / len(vals) if vals else None

    summary = {"cases": len(results), "conditions": {}}
    for label in ("with_skill", "without_skill"):
        pass_rates = [r["conditions"][label]["pass_rate"] for r in results if label in r["conditions"]]
        tokens = [r["conditions"][label]["timing"].get("total_tokens") for r in results if label in r["conditions"]]
        durations = [r["conditions"][label]["timing"].get("duration_ms") for r in results if label in r["conditions"]]
        costs = [r["conditions"][label]["timing"].get("cost_usd") for r in results if label in r["conditions"]]
        summary["conditions"][label] = {
            "mean_pass_rate": avg(pass_rates),
            "mean_total_tokens": avg(tokens),
            "mean_duration_ms": avg(durations),
            "mean_cost_usd": avg(costs),
        }
    with_pr = summary["conditions"]["with_skill"]["mean_pass_rate"]
    without_pr = summary["conditions"]["without_skill"]["mean_pass_rate"]
    if with_pr is not None and without_pr is not None:
        summary["pass_rate_delta"] = with_pr - without_pr
    return summary


def write_markdown(summary: dict, results: list[dict], out_path: Path) -> None:
    lines = ["# kotlin-coroutines-skill eval metrics", ""]
    lines.append(f"Cases graded: {summary['cases']}")
    lines.append("")
    lines.append("| Condition | Mean pass rate | Mean tokens | Mean duration (ms) | Mean cost (USD) |")
    lines.append("|---|---|---|---|---|")
    for label in ("with_skill", "without_skill"):
        c = summary["conditions"][label]
        lines.append(
            f"| {label} | {fmt(c['mean_pass_rate'], '.1%')} | {fmt(c['mean_total_tokens'], '.0f')} "
            f"| {fmt(c['mean_duration_ms'], '.0f')} | {fmt(c['mean_cost_usd'], '.4f')} |"
        )
    if "pass_rate_delta" in summary:
        lines.append("")
        lines.append(f"**Pass-rate delta (with - without): {summary['pass_rate_delta']:+.1%}**")

    lines.append("")
    lines.append("## Per-case detail")
    lines.append("")
    lines.append("| id | slug | with_skill | without_skill |")
    lines.append("|---|---|---|---|")
    for r in results:
        w = r["conditions"].get("with_skill", {})
        wo = r["conditions"].get("without_skill", {})
        lines.append(
            f"| {r['id']} | {r['slug']} | {w.get('met', '?')}/{w.get('total', '?')} "
            f"| {wo.get('met', '?')}/{wo.get('total', '?')} |"
        )
    out_path.write_text("\n".join(lines) + "\n")


def fmt(v, spec: str) -> str:
    return format(v, spec) if v is not None else "n/a"


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--workspace", type=Path, default=DEFAULT_WORKSPACE)
    ap.add_argument("--iteration", type=int, default=1)
    ap.add_argument("--judge-model", default="haiku", help="Model used only for grading, kept separate from the generating model")
    ap.add_argument("--ids", type=str, default=None, help="Comma-separated eval ids to grade, e.g. 1,5,28")
    args = ap.parse_args()

    install_info = load_install_info(args.workspace)
    plugin_id = (install_info or {}).get("plugin_id", CLAUDE_PLUGIN_ID)

    evals = load_evals()
    if args.ids:
        wanted = {int(x) for x in args.ids.split(",")}
        evals = [e for e in evals if e["id"] in wanted]

    iteration_dir = args.workspace / f"iteration-{args.iteration}"
    print(f"Grading {len(evals)} case(s) with judge model={args.judge_model}, plugin disabled for judge calls")

    results = []
    for i, case in enumerate(evals, 1):
        slug = case.get("slug") or f"eval-{case['id']}"
        case_dir = iteration_dir / f"eval-{slug}"
        print(f"[{i}/{len(evals)}] grading id={case['id']} slug={slug}")
        result = grade_case(case, case_dir, args.judge_model, plugin_id)
        if result:
            results.append(result)

    if not results:
        print("No cases graded (no run_evals.py output found). Run install_skill.py + run_evals.py first.", file=sys.stderr)
        return 1

    summary = aggregate(results)
    (iteration_dir / "metrics.json").write_text(json.dumps({"summary": summary, "cases": results}, indent=2))
    write_markdown(summary, results, iteration_dir / "metrics.md")

    print(f"\nGraded {len(results)}/{len(evals)} cases.")
    print(f"Mean pass rate -- with_skill: {fmt(summary['conditions']['with_skill']['mean_pass_rate'], '.1%')}, "
          f"without_skill: {fmt(summary['conditions']['without_skill']['mean_pass_rate'], '.1%')}")
    print(f"Wrote {iteration_dir / 'metrics.json'} and {iteration_dir / 'metrics.md'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
