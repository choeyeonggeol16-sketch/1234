#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/src" "$TMP/classes"
cp -r "$ROOT/docs/phase87_audit/tests/world/." "$TMP/src/"
PKG="$TMP/src/l1j/server/server/model/network/core/broadcast"
cp "$ROOT/src/l1j/server/server/model/network/core/broadcast/"{CAsyncSendConstBuffer.java,CAsyncSendConstBufferExceptSelf.java,CSharedBroadcast.java,CTLMFilter.java} "$PKG/"
cp "$ROOT/docs/phase114_broadcast_only/tests/VisualDeliveryFailure114.java" "$PKG/"
find "$TMP/src" -name '*.java' > "$TMP/sources"
javac -encoding UTF-8 --enable-preview --release 21 -d "$TMP/classes" @"$TMP/sources"
java --enable-preview -cp "$TMP/classes" l1j.server.server.model.network.core.broadcast.VisualDeliveryFailure114
