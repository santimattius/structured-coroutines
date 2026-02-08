#!/usr/bin/env bash
# Run official Agent Skills validator (skills-ref) on this skill.
# Requires: clone from https://github.com/agentskills/agentskills into project root as .tools-agentskills
# and pip install click strictyaml (and optionally hatchling) in .tools-agentskills/skills-ref/.venv

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SKILLS_REF="$REPO_ROOT/.tools-agentskills/skills-ref"

if [[ ! -d "$SKILLS_REF/src/skills_ref" ]]; then
  echo "skills-ref not found. Clone with:"
  echo "  git clone --depth 1 https://github.com/agentskills/agentskills.git $REPO_ROOT/.tools-agentskills"
  echo "Then install deps: cd $SKILLS_REF && python3 -m venv .venv && .venv/bin/pip install click strictyaml hatchling"
  exit 1
fi

if [[ ! -d "$SKILLS_REF/.venv" ]]; then
  echo "Creating venv and installing dependencies..."
  (cd "$SKILLS_REF" && python3 -m venv .venv && .venv/bin/pip install click strictyaml hatchling)
fi

PYTHONPATH="$SKILLS_REF/src" "$SKILLS_REF/.venv/bin/python" -m skills_ref.cli validate "$SCRIPT_DIR"
