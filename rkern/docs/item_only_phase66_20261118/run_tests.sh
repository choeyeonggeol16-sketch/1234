#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export LANG=C.UTF-8 LC_ALL=C.UTF-8
python3 "$ROOT/docs/item_only_phase66_20261118/test_phase66.py"
python3 "$ROOT/docs/item_only_phase66_20261118/test_movement_consolidation.py"
