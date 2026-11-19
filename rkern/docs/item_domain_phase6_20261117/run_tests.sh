#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export LANG=C.UTF-8 LC_ALL=C.UTF-8
python3 "$ROOT/docs/item_domain_phase6_20261117/verify_structure.py"
python3 "$ROOT/docs/item_domain_phase6_20261117/test_typed_routers.py"
bash "$ROOT/docs/item_remaining_domain_split_20261117/run_tests.sh"
bash "$ROOT/docs/phase101_broadcast_scope/run_syntax.sh"
echo 'PASS phase6 item domain regression suite'
