#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export LANG=C.UTF-8 LC_ALL=C.UTF-8
python3 "$ROOT/docs/item_domain_phase7_20261117/verify_extraction.py"
bash "$ROOT/docs/item_domain_phase6_20261117/run_tests.sh"
echo 'PASS phase7 item-use domain refactor and prior regressions'
