#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
D="$ROOT/docs/item_domain_phase30_20261118"
export LANG=C.UTF-8 LC_ALL=C.UTF-8
python3 "$D/test_phase30_scope.py"
python3 "$D/test_phase30_registry.py"
python3 "$D/test_phase30_messages.py"
python3 "$ROOT/docs/item_domain_phase21_20261118/test_phase21_enchant_typed.py"
python3 "$ROOT/docs/item_domain_phase21_20261118/test_phase21_formatting.py"
# Prior test's exact old-body assertion intentionally excludes the phase28 concatenation rewrite.
python3 - "$ROOT" <<'PY'
import sys,runpy
from pathlib import Path
m=runpy.run_path(str(Path(sys.argv[1])/'docs/item_domain_phase21_20261118/test_phase21_typed.py'))
for f in ('message_test','package_test','raid_test','format_test'):m[f]()
PY
python3 "$ROOT/docs/item_domain_phase20_20261118/test_phase20_typed.py"
for t in test_phase19_ice_typed.py test_phase19_movement_typed.py test_phase19_starter_typed.py test_phase19_formatting.py; do
 python3 "$ROOT/docs/item_domain_phase19_20261118/$t"
done
python3 "$ROOT/docs/item_domain_phase16_20261118/test_phase16_gems.py"
python3 "$ROOT/docs/item_domain_phase11_20261118/test_weapon_compile.py"
bash "$ROOT/docs/phase114_broadcast_only/run_tests.sh"
