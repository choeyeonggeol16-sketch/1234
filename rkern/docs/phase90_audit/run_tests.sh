#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
bash "$ROOT/docs/phase87_audit/run_tests.sh"
bash "$ROOT/docs/phase88_audit/tests/run_wire_test.sh"
bash "$ROOT/docs/phase89_audit/tests/run_wire.sh"
bash "$ROOT/docs/phase89_audit/tests/run_message_test.sh"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/world" "$TMP/classes/l1j/server/server/model/network/core/broadcast"
cp -r "$ROOT/docs/phase87_audit/tests/world/." "$TMP/world/"
cp "$ROOT/docs/phase90_audit/tests/DoubleGroupFanout90.java" "$TMP/world/l1j/server/server/model/network/core/broadcast/"
SHARED="$ROOT/src/l1j/server/server/model/network/core/broadcast"
cp "$SHARED/CSharedBroadcast.java" "$SHARED/CTLMFilter.java" "$SHARED/CAsyncSendConstBuffer.java" "$SHARED/CAsyncSendConstBufferExceptSelf.java" "$TMP/world/l1j/server/server/model/network/core/broadcast/"
find "$TMP/world" -name '*.java' > "$TMP/files"
javac --enable-preview --release 21 -d "$TMP/classes" @"$TMP/files"
java --enable-preview -cp "$TMP/classes" l1j.server.server.model.network.core.broadcast.DoubleGroupFanout90
