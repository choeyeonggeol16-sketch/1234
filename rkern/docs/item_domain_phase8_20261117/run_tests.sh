#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export LANG=C.UTF-8 LC_ALL=C.UTF-8
python3 "$ROOT/docs/item_domain_phase8_20261117/verify_extraction.py"
python3 "$ROOT/docs/item_domain_phase8_20261117/test_router.py"
python3 "$ROOT/docs/item_domain_phase8_20261117/test_doll_runtime.py"
bash "$ROOT/docs/item_domain_phase7_20261117/run_tests.sh"
echo 'PASS phase8 item-use functional handlers and prior item + broadcast regression suite'
