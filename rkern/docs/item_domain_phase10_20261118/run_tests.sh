#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export LANG=C.UTF-8 LC_ALL=C.UTF-8
python3 "$ROOT/docs/item_domain_phase10_20261118/test_phase10.py"
python3 "$ROOT/docs/item_domain_phase9_20261118/test_phase9.py"
python3 "$ROOT/docs/item_domain_phase8_20261117/test_router.py"
python3 "$ROOT/docs/item_domain_phase8_20261117/test_doll_runtime.py"
bash "$ROOT/docs/phase114_broadcast_only/run_tests.sh"
