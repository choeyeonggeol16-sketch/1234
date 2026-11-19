#!/usr/bin/env bash
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DOC="$ROOT/docs/item_multi_phase169_175_20261118"
python3 "$DOC/test_protection_java.py" "$ROOT"
python3 "$DOC/test_weapon_java.py" "$ROOT"
python3 "$DOC/test_previous_resolution.py" "$ROOT"
python3 "$ROOT/docs/item_multi_phase164_168_20261118/test_atomic_enchant_receipt.py" "$ROOT"
python3 "$ROOT/docs/item_multi_phase164_168_20261118/test_atomic_enchant_effects.py" "$ROOT"
python3 "$DOC/test_previous_receipts.py" "$ROOT"
python3 "$ROOT/docs/item_multi_phase159_163_20261118/check_completion.py" "$ROOT/src"
python3 "$ROOT/docs/item_multi_phase159_163_20261118/check_enchant_rollback.py" "$ROOT/src"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
javac --release 21 -encoding UTF-8 -d "$TMP" "$ROOT/docs/item_multi_phase164_168_20261118/ParseSource.java"
java -Xmx2g -cp "$TMP" ParseSource "$ROOT/src"
