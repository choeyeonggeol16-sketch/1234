#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
python3 "$HERE/make_typed.py" "$TMP/typed"
mkdir -p "$TMP/classes"
javac --release 21 -encoding UTF-8 -d "$TMP/classes" \
  "$TMP/typed/QuestLogic.java" \
  "$TMP/typed/l1j/server/server/model/L1InventoryTransaction.java" \
  "$TMP/typed/l1j/server/server/model/Instance/L1ItemInstance.java" \
  "$HERE/TestPhase54_57.java" "$HERE/TestPhase58_59.java"
java -Xmx512m -cp "$TMP/classes" TestPhase54_57
java -Xmx512m -cp "$TMP/classes" TestPhase58_59
bash "$ROOT/docs/item_domain_phase49_53_20261118/run_tests.sh"
