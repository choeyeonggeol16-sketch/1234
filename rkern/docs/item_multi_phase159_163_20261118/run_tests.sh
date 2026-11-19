#!/usr/bin/env bash
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
HERE="$ROOT/docs/item_multi_phase159_163_20261118"
python3 "$HERE/check_weapon.py" "$ROOT/src"
python3 "$HERE/check_armor_extended.py" "$ROOT/src"
python3 "$HERE/check_completion.py" "$ROOT/src"
python3 "$HERE/check_enchant_rollback.py" "$ROOT/src"
python3 "$ROOT/docs/item_multi_phase155_158_20261118/check_receipts.py" "$ROOT/src"
python3 "$ROOT/docs/item_multi_phase155_158_20261118/check_armor.py" "$ROOT/src"
python3 "$ROOT/docs/item_multi_phase155_158_20261118/check_enchant.py" "$ROOT/src"
