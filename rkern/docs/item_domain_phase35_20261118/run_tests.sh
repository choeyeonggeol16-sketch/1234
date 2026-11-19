#!/bin/bash
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
D="$ROOT/docs/item_domain_phase35_20261118"
T=$(mktemp -d);trap 'rm -rf "$T"' EXIT
javac -encoding UTF-8 --release 21 -d "$T" "$D"/typed/*.java "$D/ParseAllJava.java"
java -Xmx256m -cp "$T" TestPhase35
java -Xmx1024m -cp "$T" ParseAllJava "$ROOT/src"
