#!/usr/bin/env bash
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
T=$(mktemp -d);trap 'rm -rf "$T"' EXIT
javac --release 21 -encoding UTF-8 -d "$T" "$ROOT/docs/item_domain_phase37_20261118/typed/"*.java
java -Xmx256m -cp "$T" TestPhase37
