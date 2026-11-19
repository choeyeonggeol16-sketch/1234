#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
cp -r "$ROOT/docs/phase87_audit/tests/world/." "$TMP/"
cp "$ROOT/src/l1j/server/server/model/network/core/broadcast/"{CSharedBroadcast.java,CTLMFilter.java,CAsyncSendConstBuffer.java,CAsyncSendConstBufferExceptSelf.java} "$TMP/l1j/server/server/model/network/core/broadcast/"
if [[ -n "${RKERN_TEST_SHARED_BROADCAST:-}" ]]; then
  cp "$RKERN_TEST_SHARED_BROADCAST" "$TMP/l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java"
fi
cp "$ROOT/docs/phase101_broadcast_scope/tests/WorldOptionalRegression100.java" "$TMP/l1j/server/server/model/network/core/broadcast/"
find "$TMP" -name '*.java' -print > "$TMP/sources"
mkdir -p "$TMP/classes"
javac --enable-preview --release 21 -encoding UTF-8 -d "$TMP/classes" @"$TMP/sources"
java --enable-preview -cp "$TMP/classes" l1j.server.server.model.network.core.broadcast.WorldOptionalRegression100
