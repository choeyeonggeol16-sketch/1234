#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)";ROOT="$(cd "$HERE/../.." && pwd)"
TEMP="$(mktemp -d)";trap 'rm -rf "$TEMP"' EXIT
python3 "$HERE/make_typed.py" "$TEMP/typed"
mkdir -p "$TEMP/classes"
javac --release 21 -encoding UTF-8 -d "$TEMP/classes" \
 "$TEMP/typed/QuestLogic.java" "$TEMP/typed/l1j/server/server/model/L1InventoryTransaction.java" \
 "$TEMP/typed/l1j/server/server/model/Instance/L1ItemInstance.java" "$HERE/TestPhase63_64.java"
java -Xmx512m -cp "$TEMP/classes" TestPhase63_64
bash "$ROOT/docs/item_domain_phase60_62_20261118/run_tests.sh"
