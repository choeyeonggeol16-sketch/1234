#!/usr/bin/env bash
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
D="$ROOT/docs/item_domain_phase36_20261118"
T=$(mktemp -d);trap 'rm -rf "$T"' EXIT
javac --release 21 -encoding UTF-8 -d "$T" "$D"/typed/*.java
java -Xmx256m -cp "$T" TestPhase36
