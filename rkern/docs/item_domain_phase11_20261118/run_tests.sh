#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export LANG=C.UTF-8 LC_ALL=C.UTF-8
python3 "$ROOT/docs/item_domain_phase11_20261118/test_phase11.py"
python3 "$ROOT/docs/item_domain_phase11_20261118/test_weapon_compile.py"
bash "$ROOT/docs/item_domain_phase10_20261118/run_tests.sh"
