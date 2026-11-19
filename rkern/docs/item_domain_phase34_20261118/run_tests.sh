#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export LANG=C.UTF-8 LC_ALL=C.UTF-8
python3 "$ROOT/docs/item_domain_phase34_20261118/test_grant_lifecycle.py"
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
javac -encoding UTF-8 --release 21 -d "$T" "$ROOT/docs/item_domain_phase34_20261118/ParseAllJava.java"
java -Xmx1024m -Dfile.encoding=UTF-8 -cp "$T" ParseAllJava "$ROOT/src"
bash "$ROOT/docs/item_domain_phase33_20261118/run_tests.sh"
