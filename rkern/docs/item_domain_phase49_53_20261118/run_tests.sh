#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$DIR/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
python3 "$DIR/make_typed.py" "$ROOT" "$TMP/typed"
cp "$DIR/TestPhase49_53.java" "$TMP/typed/"
cp "$ROOT"/docs/item_domain_phase38_48_20261118/typed/TestPhase*.java "$TMP/typed/"
mkdir "$TMP/classes"
javac --release 21 -encoding UTF-8 -d "$TMP/classes" "$TMP"/typed/*.java \
    "$TMP/typed/l1j/server/server/model/L1InventoryTransaction.java" \
    "$TMP/typed/l1j/server/server/model/Instance/L1ItemInstance.java"
for name in TestPhase38 TestPhase39 TestPhase40 TestPhase41_42 TestPhase43 TestPhase44_45 TestPhase46_48 TestPhase49_53; do
    java -Xmx256m -cp "$TMP/classes" "$name"
done
javac --release 21 -d "$TMP/classes" "$DIR/SyntaxCheck.java"
java -Xmx1024m -cp "$TMP/classes" SyntaxCheck "$ROOT/src"
