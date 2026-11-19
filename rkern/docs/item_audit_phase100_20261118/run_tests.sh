#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export LANG=C.UTF-8 LC_ALL=C.UTF-8
python3 "$ROOT/docs/item_audit_phase100_20261118/verify_sources.py"
python3 "$ROOT/docs/item_only_phase73_100_20261118/run_stock_guard_audit.py"
python3 "$ROOT/docs/item_only_phase73_100_20261118/run_normal_ab.py"
python3 "$ROOT/docs/item_only_phase73_100_20261118/run_edge_cases.py"
python3 "$ROOT/docs/item_only_phase73_100_20261118/run_skillbook_ab.py"
python3 "$ROOT/docs/item_only_phase73_100_20261118/run_views_ab.py"
python3 "$ROOT/docs/item_only_phase73_100_20261118/run_autoselect_ab.py"
python3 "$ROOT/docs/item_domain_phase31_20261118/test_recruit_atomic.py"
python3 "$ROOT/docs/item_domain_phase32_20261118/test_legacy_gifts_atomic.py"
bash "$ROOT/docs/item_domain_phase33_20261118/run_tests.sh"
python3 "$ROOT/docs/item_domain_phase30_20261118/test_phase30_registry.py"
bash "$ROOT/docs/item_only_phase66_20261118/run_tests.sh"
bash "$ROOT/docs/item_only_phase67_20261118/run_tests.sh"
python3 "$ROOT/docs/item_only_phase68_20261118/run_phase68.py"
python3 "$ROOT/docs/item_only_phase69_20261118/run_phase69.py"
python3 "$ROOT/docs/item_only_phase71_20261118/run_phase71.py"
python3 "$ROOT/docs/item_only_phase72_20261118/run_phase72.py"
bash "$ROOT/docs/item_domain_phase49_53_20261118/run_tests.sh"
echo 'PASS: phase 1-100 current-source audit selected independent regression suites'
