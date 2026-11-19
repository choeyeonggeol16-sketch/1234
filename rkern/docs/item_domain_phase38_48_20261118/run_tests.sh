#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SRC="$ROOT/docs/item_domain_phase38_48_20261118/typed"
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
javac --release 21 -encoding UTF-8 -d "$T" "$SRC"/*.java "$SRC"/l1j/server/server/model/L1InventoryTransaction.java "$SRC"/l1j/server/server/model/Instance/L1ItemInstance.java
for CLASS in TestPhase38 TestPhase39 TestPhase40 TestPhase41_42 TestPhase43 TestPhase44_45 TestPhase46_48; do
    java -Xmx192m -cp "$T" "$CLASS"
done
