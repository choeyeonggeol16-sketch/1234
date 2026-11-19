#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/src" "$TMP/classes"
cp -r "$ROOT/docs/phase87_audit/tests/world/." "$TMP/src/"
PKG="$TMP/src/l1j/server/server/model/network/core"
cp "$ROOT/src/l1j/server/server/model/network/core/broadcast/"{CAsyncSendConstBuffer.java,CAsyncSendConstBufferExceptSelf.java,CSharedBroadcast.java,CTLMFilter.java} "$PKG/broadcast/"
cp "$ROOT/docs/phase111_legacy_broadcast_audit/tests/GlobalFailure111.java" "$PKG/broadcast/"
find "$TMP/src" -name '*.java' > "$TMP/sources"
javac -encoding UTF-8 --enable-preview --release 21 -d "$TMP/classes" @"$TMP/sources"
java --enable-preview -cp "$TMP/classes" l1j.server.server.model.network.core.broadcast.GlobalFailure111

python3 "$ROOT/docs/phase111_legacy_broadcast_audit/test_legacy_paths.py"
