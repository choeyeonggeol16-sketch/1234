#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
AUDIT="$ROOT/docs/item_function_refactor_20261117"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
export LANG=C.UTF-8 LC_ALL=C.UTF-8
javac -encoding UTF-8 -d "$TMP" "$AUDIT/tests/ParseAllJava.java"
java -Xmx3g -cp "$TMP" ParseAllJava "$ROOT/src"
if [[ $# -gt 0 ]]; then
  python3 "$AUDIT/tests/test_item_refactor.py" "$1"
else
  python3 "$AUDIT/tests/test_item_refactor.py"
fi
bash "$ROOT/docs/phase114_broadcast_only/run_tests.sh"
