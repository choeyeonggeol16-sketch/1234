#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
javac -encoding UTF-8 -d "$TMP" "$ROOT/docs/phase101_broadcast_scope/tests/ParseAll100.java"
java -cp "$TMP" ParseAll100 "$ROOT/src"
