#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
python3 "$ROOT/docs/item_domain_phase30_audit_20261118/test_route_differential.py"
python3 "$ROOT/docs/item_domain_phase30_20261118/test_phase30_scope.py"
python3 "$ROOT/docs/item_domain_phase30_20261118/test_phase30_messages.py"
bash "$ROOT/docs/item_domain_phase30_20261118/run_tests.sh"
