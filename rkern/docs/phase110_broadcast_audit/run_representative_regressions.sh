#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
bash docs/phase110_broadcast_audit/run_new_tests.sh
bash docs/phase102_broadcast_only/run_new_tests.sh
bash docs/phase98_audit/run_tests.sh
bash docs/phase87_audit/run_tests.sh
bash docs/phase88_audit/tests/run_wire_test.sh
bash docs/phase89_audit/tests/run_wire.sh
bash docs/phase89_audit/tests/run_message_test.sh
bash docs/phase101_broadcast_scope/run_broadcast_tests.sh
bash docs/phase94_97_audit/tests/run_map_frame.sh
python3 docs/phase94_97_audit/tests/test_gate_release.py
printf '103-110 broadcast-only representative regressions PASS\n'
