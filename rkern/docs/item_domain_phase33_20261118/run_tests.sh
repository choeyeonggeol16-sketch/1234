#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export LANG=C.UTF-8 LC_ALL=C.UTF-8
python3 "$ROOT/docs/item_domain_phase33_20261118/test_creation_world_lifecycle.py"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
javac -encoding UTF-8 --release 21 -d "$TMP" "$ROOT/docs/item_domain_phase33_20261118/ParseAllJava.java"
java -Xmx1024m -Dfile.encoding=UTF-8 -cp "$TMP" ParseAllJava "$ROOT/src"
bash "$ROOT/docs/item_domain_phase32_20261118/run_tests.sh"
bash "$ROOT/docs/phase114_broadcast_only/run_tests.sh"
