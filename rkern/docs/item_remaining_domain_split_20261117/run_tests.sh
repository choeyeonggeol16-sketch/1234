#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
python3 "$HERE/tests/test_structure.py"
python3 "$HERE/tests/test_router_java21.py"
bash "$HERE/../item_domain_split_20261117/run_tests.sh"
bash "$HERE/../phase114_broadcast_only/run_tests.sh" >/dev/null 2>&1
echo 'PASS prior item domain tests + broadcast 114 tests'
