#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export LANG=C.UTF-8 LC_ALL=C.UTF-8
python3 "$ROOT/docs/item_domain_phase31_20261118/test_recruit_atomic.py"
python3 "$ROOT/docs/item_domain_phase30_audit_20261118/test_route_differential.py"
python3 "$ROOT/docs/item_domain_phase30_20261118/test_phase30_scope.py"
python3 "$ROOT/docs/item_domain_phase30_20261118/test_phase30_registry.py"
python3 "$ROOT/docs/item_domain_phase30_20261118/test_phase30_messages.py"
python3 "$ROOT/docs/item_domain_phase19_20261118/test_phase19_ice_typed.py"
python3 "$ROOT/docs/item_domain_phase19_20261118/test_phase19_movement_typed.py"
python3 "$ROOT/docs/item_domain_phase20_20261118/test_phase20_typed.py"
python3 "$ROOT/docs/item_domain_phase16_20261118/test_phase16_gems.py"
python3 "$ROOT/docs/item_domain_phase11_20261118/test_weapon_compile.py"
bash "$ROOT/docs/phase114_broadcast_only/run_tests.sh"
