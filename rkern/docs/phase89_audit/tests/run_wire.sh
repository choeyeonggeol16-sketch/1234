#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
find "$ROOT/docs/phase89_audit/tests/stubs" -name '*.java' > "$T/sources"
printf '%s\n' "$ROOT/src/l1j/server/server/packets/Protocol/MainServer/Proto/ProtoWriter.java" "$ROOT/docs/phase89_audit/tests/ProtoWireParity89.java" >> "$T/sources"
javac --release 21 -d "$T/classes" @"$T/sources"
java -cp "$T/classes" l1j.server.server.packets.Protocol.MainServer.Proto.ProtoWireParity89
