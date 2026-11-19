#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
python3 docs/phase101_broadcast_scope/tests/test_notice_boundaries.py
python3 docs/phase101_broadcast_scope/tests/test_orim_leader_broadcast.py
bash docs/phase101_broadcast_scope/tests/run_optional_group.sh
bash docs/phase101_broadcast_scope/tests/run_phase93_world.sh
python3 docs/phase93_audit/tests/test_source_boundaries.py
python3 docs/phase93_audit/tests/test_orim_failure.py
python3 docs/phase93_audit/tests/test_lfc_finish_failure.py
echo 'Broadcast-only scope/functional regressions PASS'
