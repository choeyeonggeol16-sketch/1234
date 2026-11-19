#!/usr/bin/env bash
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
HERE="$ROOT/docs/item_multi_phase155_158_20261118"
python3 "$HERE/check_enchant.py" "$ROOT/src"
python3 "$HERE/check_armor.py" "$ROOT/src"
python3 "$HERE/check_receipts.py" "$ROOT/src"
bash "$ROOT/docs/item_multi_phase146_154_20261118/run_tests.sh"
bash "$ROOT/docs/item_multi_phase145_20261118/run_tests.sh"
bash "$ROOT/docs/item_multi_phase143_144_20261118/run_tests.sh"
