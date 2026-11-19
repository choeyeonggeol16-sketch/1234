#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export LANG=C.UTF-8 LC_ALL=C.UTF-8
D="$ROOT/docs/item_domain_phase18_20261118"
python3 "$D/test_phase18_structure.py"
python3 "$D/test_phase18_poly.py"
python3 "$D/test_phase18_teleport.py"
python3 "$D/test_phase18_identify.py"
# These suites do not require the deleted Phase10/15 level-only Java files.
for v in '17 test_phase17_structure' '17 test_phase17_typed' '16 test_phase16_structure' '16 test_phase16_gems' '14 test_phase14' '13 test_phase13' '13 test_phase13_typed' '12 test_phase12' '12 test_phase12_typed' '11 test_weapon_compile'; do
 set -- $v
 python3 "$ROOT/docs/item_domain_phase${1}_20261118/${2}.py"
done
bash "$ROOT/docs/phase114_broadcast_only/run_tests.sh"
