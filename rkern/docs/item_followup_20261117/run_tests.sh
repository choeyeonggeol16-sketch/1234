#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export LANG=C.UTF-8 LC_ALL=C.UTF-8
python3 "$ROOT/docs/item_followup_20261117/tests/test_hotpath.py"
python3 "$ROOT/docs/item_followup_20261117/tests/test_stun_rng.py"
bash "$ROOT/docs/item_function_refactor_20261117/run_tests.sh"
