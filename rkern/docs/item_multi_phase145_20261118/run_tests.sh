#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
python3 "$ROOT/docs/item_multi_phase145_20261118/generate_typed.py" "$ROOT" "$WORK/typed"
mkdir -p "$WORK/classes"
find "$WORK/typed" -name '*.java' -print0 | xargs -0 javac --release 21 -encoding UTF-8 -d "$WORK/classes"
java -cp "$WORK/classes" regression.Regression
