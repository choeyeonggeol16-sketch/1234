#!/usr/bin/env bash
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK="$(mktemp -d)";trap 'rm -rf "$WORK"' EXIT
HERE="$ROOT/docs/item_multi_phase146_154_20261118"
python3 "$HERE/make_inventory_harness.py" "$ROOT" "$WORK/inventory"
mkdir -p "$WORK/inventory/classes"
javac --release 21 -encoding UTF-8 -d "$WORK/inventory/classes" \
  "$WORK/inventory/ReplayHarness.java" \
  "$WORK/inventory/l1j/server/server/storage/CacheD.java" \
  "$WORK/inventory/l1j/server/server/model/character/scheduler/PcInventoryExpiryScheduler.java"
java -cp "$WORK/inventory/classes" ReplayHarness
python3 "$HERE/make_callback_harness.py" "$ROOT" "$WORK/callbacks"
mkdir -p "$WORK/callbacks/classes"
javac --release 21 -encoding UTF-8 -d "$WORK/callbacks/classes" "$WORK/callbacks/PostCommitHarness.java"
java -cp "$WORK/callbacks/classes" PostCommitHarness 2> "$WORK/fault-injection-expected.log"
mkdir -p "$WORK/parser"
javac --release 21 -encoding UTF-8 -d "$WORK/parser" "$HERE/ParseAll.java"
java -Xmx2g -cp "$WORK/parser" ParseAll "$ROOT/src"
