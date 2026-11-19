#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
bash docs/phase93_audit/run_tests.sh
bash docs/phase94_97_audit/tests/run_map_frame.sh
python3 docs/phase94_97_audit/tests/test_gate_release.py
