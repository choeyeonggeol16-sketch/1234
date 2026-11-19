#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
python3 "$ROOT/docs/item_multi_phase139_142_20261118/run_typed_harness.py" "$ROOT" "$WORK"
javac --release 21 -encoding UTF-8 -d "$WORK/class" "$WORK"/l1j/server/server/model/item/function/*.java
java -cp "$WORK/class" l1j.server.server.model.item.function.Regression
