#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/src" "$TMP/classes"
cp -r "$ROOT/docs/phase87_audit/tests/world/." "$TMP/src/"
PKG="$TMP/src/l1j/server/server/model/network/core/broadcast"
cp "$ROOT/src/l1j/server/server/model/network/core/broadcast/"{CAsyncSendConstBuffer.java,CAsyncSendConstBufferExceptSelf.java,CSharedBroadcast.java,CTLMFilter.java} "$PKG/"
cp "$ROOT/docs/phase113_broadcast_only/tests/OptionalFanout113.java" "$PKG/"
find "$TMP/src" -name '*.java' > "$TMP/sources"
javac -encoding UTF-8 --enable-preview --release 21 -d "$TMP/classes" @"$TMP/sources"
java --enable-preview -cp "$TMP/classes" l1j.server.server.model.network.core.broadcast.OptionalFanout113
# Strict only methods are retained for stateful callers, but all explicit
# production optional visual/notification call sites must use the safe APIs.
python3 "$ROOT/docs/phase113_broadcast_only/test_legacy_strict_calls.py"
