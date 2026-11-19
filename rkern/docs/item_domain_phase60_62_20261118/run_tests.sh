#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)";ROOT="$(cd "$HERE/../.." && pwd)"
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
python3 "$HERE/make_typed.py" "$TMP/typed"
mkdir -p "$TMP/classes"
javac --release 21 -encoding UTF-8 -d "$TMP/classes" \
 "$TMP/typed/QuestLogic.java" \
 "$TMP/typed/l1j/server/server/model/L1InventoryTransaction.java" \
 "$TMP/typed/l1j/server/server/model/Instance/L1ItemInstance.java" \
 "$HERE/TestPhase60_62.java"
java -Xmx512m -cp "$TMP/classes" TestPhase60_62
bash "$ROOT/docs/item_domain_phase54_59_20261118/run_tests.sh"
