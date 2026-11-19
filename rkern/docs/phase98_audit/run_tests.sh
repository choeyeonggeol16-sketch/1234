#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
T="$ROOT/docs/phase98_audit/tests"
javac --enable-preview --release 21 -d "$OUT" \
  "$T/transport/CIOServer.java" \
  "$T/connection/CUserSocket.java" \
  "$T/broadcast/CSharedBroadcast.java" \
  "$T/broadcast/SourceSafety98.java" \
  "$ROOT/src/l1j/server/server/model/network/core/broadcast/CAsyncSendConstBuffer.java"
java --enable-preview -cp "$OUT" l1j.server.server.model.network.core.broadcast.SourceSafety98
