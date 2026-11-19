#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
for test in \
  test_direct_commit.py \
  test_direct_abort.py \
  test_begin_rollback.py \
  test_buffer_release.py \
  test_write_completion.py \
  test_close_cleanup.py \
  test_read_close.py; do
  python3 "docs/phase99_audit/tests/$test"
done
# Reuse the existing 98th production Native-source fixture and representative
# historical packet/AOI/fanout regressions; no new worker or transport.
bash docs/phase98_audit/run_tests.sh
bash docs/phase87_audit/run_tests.sh
bash docs/phase88_audit/tests/run_wire_test.sh
bash docs/phase89_audit/tests/run_wire.sh
bash docs/phase89_audit/tests/run_message_test.sh
bash docs/phase90_audit/run_tests.sh
python3 docs/phase92_audit/tests/test_game_notice_failure.py
python3 docs/phase93_audit/tests/test_orim_failure.py
python3 docs/phase93_audit/tests/test_lfc_finish_failure.py
python3 docs/phase93_audit/tests/test_source_boundaries.py
bash docs/phase94_97_audit/tests/run_map_frame.sh
python3 docs/phase94_97_audit/tests/test_gate_release.py
