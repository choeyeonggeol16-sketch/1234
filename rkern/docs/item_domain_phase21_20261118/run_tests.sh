#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export LANG=C.UTF-8 LC_ALL=C.UTF-8
D="$ROOT/docs/item_domain_phase21_20261118"
python3 "$D/test_phase21_typed.py"
python3 "$D/test_phase21_enchant_typed.py"
python3 "$D/test_phase21_formatting.py"
python3 "$ROOT/docs/item_domain_phase20_20261118/test_phase20_typed.py"
for test in test_phase19_ice_typed.py test_phase19_movement_typed.py test_phase19_starter_typed.py test_phase19_formatting.py; do
  python3 "$ROOT/docs/item_domain_phase19_20261118/$test"
done
python3 "$ROOT/docs/item_domain_phase16_20261118/test_phase16_gems.py"
python3 "$ROOT/docs/item_domain_phase11_20261118/test_weapon_compile.py"
bash "$ROOT/docs/phase114_broadcast_only/run_tests.sh"
