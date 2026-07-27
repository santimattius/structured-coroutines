#!/usr/bin/env python3
"""Download and install kotlin-coroutines-skill into a real local project,
the way an actual consumer would, so eval metrics reflect the installable
plugin rather than this dev machine's already-registered global config.

Verified against Claude Code CLI 2.1.220 on this machine:

  claude plugin marketplace add <source>          # "download" the skill
  claude plugin install <plugin>@<marketplace> \\
      -s local                                    # "install" it, scoped
                                                    # to one project only
                                                    # (writes
                                                    # <project>/.claude/settings.local.json,
                                                    # gitignored -- never
                                                    # touches this repo's
                                                    # own tracked files or
                                                    # ~/.claude/settings.json)

Both commands are idempotent (verified: re-running either just prints
"already added" / "already installed" and exits 0), so this script is safe
to re-run.

Does NOT use an isolated CLAUDE_CONFIG_DIR: verified that breaks `-p` auth
(OAuth/keychain are only ever read from the real user config; see
`claude --help`), and the with/without-skill split already gets a clean
baseline via the --settings enabledPlugins override in run_evals.py, so
full config isolation isn't needed to get an honest without_skill arm.
"""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
MARKETPLACE_JSON = REPO_ROOT / ".claude-plugin" / "marketplace.json"
DEFAULT_WORKSPACE = REPO_ROOT.parent / "kotlin-coroutines-skill-workspace"
DEFAULT_SAMPLE_PROJECT = REPO_ROOT / "sample"

# Directories that are safe/expected to skip when copying the sample project
# into the workspace -- build output and IDE/VCS state, not source.
SKIP_DIRS = {"build", ".gradle", ".git", ".idea", ".kotlin"}


def read_marketplace_name() -> str:
    data = json.loads(MARKETPLACE_JSON.read_text())
    return data["name"]


def read_plugin_name() -> str:
    data = json.loads(MARKETPLACE_JSON.read_text())
    return data["plugins"][0]["name"]


def run(cmd: list[str], cwd: Path | None = None) -> str:
    proc = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, timeout=60)
    if proc.returncode != 0:
        raise RuntimeError(f"{' '.join(cmd)} exited {proc.returncode}:\n{proc.stderr}")
    print(f"  $ {' '.join(cmd)}\n    {proc.stdout.strip()}")
    return proc.stdout


def prepare_target_project(target: Path, sample_project: Path, force_refresh: bool) -> None:
    if target.exists() and force_refresh:
        shutil.rmtree(target)
    if target.exists():
        print(f"Target project already present, reusing: {target}")
        return
    if not sample_project.is_dir():
        raise FileNotFoundError(f"--sample-project not found: {sample_project}")
    print(f"Copying {sample_project} -> {target}")
    shutil.copytree(
        sample_project,
        target,
        ignore=shutil.ignore_patterns(*SKIP_DIRS),
    )


def install(source: str, marketplace_name: str, plugin_id: str, target: Path) -> None:
    print(f"\n[1/2] Downloading skill (adding marketplace): {source}")
    run(["claude", "plugin", "marketplace", "add", source])

    print(f"\n[2/2] Installing {plugin_id} locally into {target} (scope: local)")
    run(["claude", "plugin", "install", plugin_id, "-s", "local"], cwd=target)

    out = run(["claude", "plugin", "list"], cwd=target)
    if plugin_id.split("@")[0] not in out:
        raise RuntimeError(f"Install verification failed -- {plugin_id} not in `claude plugin list`:\n{out}")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument(
        "--source",
        default=str(REPO_ROOT),
        help="Marketplace source: local path or GitHub repo (default: this repo's working tree, "
        "since the skill isn't merged upstream yet). Pass a github.com URL to test the real "
        "published/pushed state instead of local uncommitted changes.",
    )
    ap.add_argument("--marketplace-name", default=None, help="Override marketplace name (default: read from marketplace.json)")
    ap.add_argument("--plugin", default=None, help="Override plugin name (default: read from marketplace.json)")
    ap.add_argument("--workspace", type=Path, default=DEFAULT_WORKSPACE)
    ap.add_argument(
        "--target-project",
        type=Path,
        default=None,
        help="Project directory to install into (default: <workspace>/target-project, "
        "a fresh copy of kotlin-coroutines-skill's own sample/ Kotlin project)",
    )
    ap.add_argument("--sample-project", type=Path, default=DEFAULT_SAMPLE_PROJECT)
    ap.add_argument("--force-refresh", action="store_true", help="Wipe and re-copy the target project before installing")
    args = ap.parse_args()

    marketplace_name = args.marketplace_name or read_marketplace_name()
    plugin_name = args.plugin or read_plugin_name()
    plugin_id = f"{plugin_name}@{marketplace_name}"
    target = args.target_project or (args.workspace / "target-project")

    args.workspace.mkdir(parents=True, exist_ok=True)
    prepare_target_project(target, args.sample_project, args.force_refresh)
    install(args.source, marketplace_name, plugin_id, target)

    info = {
        "source": args.source,
        "marketplace_name": marketplace_name,
        "plugin_id": plugin_id,
        "target_project": str(target),
    }
    (args.workspace / "install-info.json").write_text(json.dumps(info, indent=2))
    print(f"\nDone. Wrote {args.workspace / 'install-info.json'}")
    print(f"Next: python3 {REPO_ROOT / 'tools' / 'scripts' / 'run_evals.py'} --workspace {args.workspace}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
