#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/src" "$TMP/classes"
cp -r "$ROOT/docs/phase87_audit/tests/world/." "$TMP/src/"
PKG="$TMP/src/l1j/server/server/model/network/core"
cp "$ROOT/src/l1j/server/server/model/network/core/broadcast/"{CAsyncSendConstBuffer.java,CAsyncSendConstBufferExceptSelf.java,CSharedBroadcast.java,CTLMFilter.java} "$PKG/broadcast/"
mkdir -p "$PKG/sc/packet" "$TMP/src/l1j/server/server/model/network/buffer"
cp "$ROOT/src/l1j/server/server/model/network/core/sc/packet/SC_ChatPacket.java" "$PKG/sc/packet/SC_ChatPacket.java"
cp "$ROOT/docs/phase102_broadcast_only/tests/BroadcastBoundary102.java" "$PKG/broadcast/"
cat > "$TMP/src/l1j/server/server/model/network/buffer/WriteBuffer.java" <<'JAVA'
package l1j.server.server.model.network.buffer;
public class WriteBuffer {
 public void writeC(int x){} public void writeD(int x){} public void writeH(int x){}
 public void writeS(String x){}
}
JAVA
cat > "$PKG/connection/CIOFrame.java" <<'JAVA'
package l1j.server.server.model.network.core.connection;
import l1j.server.server.model.network.buffer.WriteBuffer;
public class CIOFrame { public WriteBuffer buffer(){return new WriteBuffer();} }
JAVA
# Extend only the test stubs; never modify production socket/transport code.
python3 - "$TMP/src" <<'PY'
import sys
from pathlib import Path
root=Path(sys.argv[1])
p=root/'l1j/server/server/model/network/core/connection/CUserSocket.java'
s=p.read_text();idx=s.rfind('}')
s=s[:idx]+''' public CIOFrame beginDirectFrame(int size){return new CIOFrame();}
 public boolean commitDirectFrame(CIOFrame frame){return true;}
 public void abortDirectFrame(CIOFrame frame){}
'''+s[idx:];p.write_text(s)
p=root/'l1j/server/server/model/Instance/L1PcInstance.java';s=p.read_text();idx=s.rfind('}');s=s[:idx]+' public String getName(){return "Tester";}\n'+s[idx:];p.write_text(s)
p=root/'l1j/server/server/Opcodes.java';s=p.read_text().replace('S_MOVE_OBJECT=2;', 'S_MOVE_OBJECT=2,S_MESSAGE=3,S_SAY=4;');p.write_text(s)
PY
find "$TMP/src" -name '*.java' > "$TMP/sources"
javac -encoding UTF-8 --enable-preview --release 21 -d "$TMP/classes" @"$TMP/sources"
java --enable-preview -cp "$TMP/classes" l1j.server.server.model.network.core.broadcast.BroadcastBoundary102
