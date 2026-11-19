#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export LANG=C.UTF-8 LC_ALL=C.UTF-8
python3 "$ROOT/docs/item_domain_phase17_20261118/test_phase17_structure.py"
python3 "$ROOT/docs/item_domain_phase17_20261118/test_phase17_typed.py"
bash "$ROOT/docs/item_domain_phase16_20261118/run_tests.sh"
