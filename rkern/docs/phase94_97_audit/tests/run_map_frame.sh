#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
cp -r "$ROOT/docs/phase87_audit/tests/world/." "$TMP/"
mkdir -p "$TMP/l1j/server/server/model/network/core/broadcast"
cp "$ROOT"/src/l1j/server/server/model/network/core/broadcast/{CSharedBroadcast.java,CAsyncSendConstBuffer.java,CAsyncSendConstBufferExceptSelf.java,CTLMFilter.java} "$TMP/l1j/server/server/model/network/core/broadcast/"
cp "$ROOT/docs/phase94_97_audit/tests/MultiFrameRelease94.java" "$TMP/l1j/server/server/model/network/core/broadcast/"
find "$TMP" -name '*.java' > "$TMP/sources"
javac --enable-preview --release 21 -encoding UTF-8 -d "$TMP/classes" @"$TMP/sources"
java --enable-preview -cp "$TMP/classes" l1j.server.server.model.network.core.broadcast.MultiFrameRelease94
