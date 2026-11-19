#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
bash docs/phase90_audit/run_tests.sh
python3 docs/phase91_audit/tests/run_failures.py
python3 docs/phase91_audit/tests/run_combat.py
