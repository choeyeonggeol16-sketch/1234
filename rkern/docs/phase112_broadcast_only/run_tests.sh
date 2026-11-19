#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/src" "$TMP/classes"
cp -r "$ROOT/docs/phase87_audit/tests/world/." "$TMP/src/"
PKG="$TMP/src/l1j/server/server/model/network/core/broadcast"
cp "$ROOT/src/l1j/server/server/model/network/core/broadcast/"{CAsyncSendConstBuffer.java,CAsyncSendConstBufferExceptSelf.java,CSharedBroadcast.java,CTLMFilter.java} "$PKG/"
cp "$ROOT/docs/phase112_broadcast_only/tests/"{OptionalInvalidSource112.java,LargeFrameFailure112.java} "$PKG/"
find "$TMP/src" -name '*.java' > "$TMP/sources"
javac -encoding UTF-8 --enable-preview --release 21 -d "$TMP/classes" @"$TMP/sources"
java --enable-preview -cp "$TMP/classes" l1j.server.server.model.network.core.broadcast.OptionalInvalidSource112
java --enable-preview -cp "$TMP/classes" l1j.server.server.model.network.core.broadcast.LargeFrameFailure112
python3 "$ROOT/docs/phase112_broadcast_only/test_packet_entrypoints.py"
python3 "$ROOT/docs/phase112_broadcast_only/test_remaining_entrypoints.py"
