#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
python3 "$ROOT/docs/item_domain_phase32_20261118/test_legacy_gifts_atomic.py"
python3 "$ROOT/docs/item_domain_phase31_20261118/test_recruit_atomic.py"
python3 "$ROOT/docs/item_domain_phase30_audit_20261118/test_route_differential.py"
python3 "$ROOT/docs/item_domain_phase30_20261118/test_phase30_registry.py"
python3 "$ROOT/docs/item_domain_phase19_20261118/test_phase19_ice_typed.py"
python3 "$ROOT/docs/item_domain_phase19_20261118/test_phase19_movement_typed.py"
python3 "$ROOT/docs/item_domain_phase20_20261118/test_phase20_typed.py"
