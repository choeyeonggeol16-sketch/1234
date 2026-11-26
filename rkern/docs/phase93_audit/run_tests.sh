#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
bash docs/phase91_audit/run_tests.sh
python3 docs/phase92_audit/tests/test_game_notice_failure.py
python3 docs/phase93_audit/tests/test_orim_failure.py
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
cp -r docs/phase87_audit/tests/world/. "$TMP/"
cp src/l1j/server/server/model/network/core/broadcast/{CSharedBroadcast.java,CTLMFilter.java,CAsyncSendConstBuffer.java,CAsyncSendConstBufferExceptSelf.java} "$TMP/l1j/server/server/model/network/core/broadcast/"
cp docs/phase93_audit/tests/WorldOptionalRegression93.java "$TMP/l1j/server/server/model/network/core/broadcast/"
find "$TMP" -name '*.java' > "$TMP/sources"
mkdir -p "$TMP/classes"
javac --enable-preview --release 21 -d "$TMP/classes" @"$TMP/sources"
java --enable-preview -cp "$TMP/classes" l1j.server.server.model.network.core.broadcast.WorldOptionalRegression93
python3 docs/phase93_audit/tests/test_source_boundaries.py
