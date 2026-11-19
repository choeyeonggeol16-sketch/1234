#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export LANG=C.UTF-8 LC_ALL=C.UTF-8
python3 "$ROOT/docs/item_domain_phase16_20261118/test_phase16_structure.py"
python3 "$ROOT/docs/item_domain_phase16_20261118/test_phase16_gems.py"
# The Phase15 suite includes Java21 production syntax parsing and earlier item /
# broadcast regression harnesses, without changing any production files.
bash "$ROOT/docs/item_domain_phase15_20261118/run_tests.sh"
