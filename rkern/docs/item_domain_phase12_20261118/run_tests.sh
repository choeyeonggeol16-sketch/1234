#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export LANG=C.UTF-8 LC_ALL=C.UTF-8
python3 "$ROOT/docs/item_domain_phase12_20261118/test_phase12.py"
python3 "$ROOT/docs/item_domain_phase12_20261118/test_phase12_typed.py"
bash "$ROOT/docs/item_domain_phase11_20261118/run_tests.sh"
bash "$ROOT/docs/phase101_broadcast_scope/run_syntax.sh"
