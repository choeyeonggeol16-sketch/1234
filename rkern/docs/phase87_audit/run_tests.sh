#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
SHARED="$ROOT/src/l1j/server/server/model/network/core/broadcast"
# First: actual production CAsync buffer source paired with the known simple socket/Native pool mock.
mkdir -p "$TMP/simple" "$TMP/simple_classes"
cp -r "$SCRIPT_DIR/tests/simple/". "$TMP/simple/"
mkdir -p "$TMP/simple/l1j/server/server/model/network/core/broadcast"
cp "$SHARED/CAsyncSendConstBuffer.java" "$SHARED/CAsyncSendConstBufferExceptSelf.java" "$TMP/simple/l1j/server/server/model/network/core/broadcast/"
find "$TMP/simple" -name '*.java' > "$TMP/simple_sources"
javac --enable-preview --release 21 -d "$TMP/simple_classes" @"$TMP/simple_sources"
java --enable-preview -cp "$TMP/simple_classes" l1j.server.server.model.network.core.broadcast.BroadcastRegression
java --enable-preview -cp "$TMP/simple_classes" l1j.server.server.model.network.core.broadcast.BroadcastStress87
# Second: actual CSharedBroadcast, CTLMFilter and two production buffer classes with a stub L1 world.
mkdir -p "$TMP/world" "$TMP/world_classes"
cp -r "$SCRIPT_DIR/tests/world/". "$TMP/world/"
mkdir -p "$TMP/world/l1j/server/server/model/network/core/broadcast"
cp "$SHARED/CSharedBroadcast.java" "$SHARED/CTLMFilter.java" "$SHARED/CAsyncSendConstBuffer.java" "$SHARED/CAsyncSendConstBufferExceptSelf.java" "$TMP/world/l1j/server/server/model/network/core/broadcast/"
find "$TMP/world" -name '*.java' > "$TMP/world_sources"
javac --enable-preview --release 21 -d "$TMP/world_classes" @"$TMP/world_sources"
java --enable-preview -cp "$TMP/world_classes" l1j.server.server.model.network.core.broadcast.WorldFanoutRegression87
# Third: syntax-only parse of all production Java, not a dependency/type compile.
mkdir -p "$TMP/parse_classes"
EXPORTS=(--add-exports jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED)
javac "${EXPORTS[@]}" -d "$TMP/parse_classes" "$SCRIPT_DIR/tests/ParseJavaAll.java"
java "${EXPORTS[@]}" -cp "$TMP/parse_classes" ParseJavaAll "$ROOT/src"
