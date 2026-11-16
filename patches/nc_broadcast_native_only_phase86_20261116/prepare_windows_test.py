#!/usr/bin/env python3
"""Phase86 regression: use Native writer factories in old buffer lifetime oracles.

Capture immutable test byte fixtures at NativeWriter construction time, so
the same post-constructor mutation/release/exclusion tests still run.
"""
import sys,subprocess,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
WORK=Path(sys.argv[1]).resolve()
subprocess.run([sys.executable,
  str(ROOT/"patches/nc_broadcast_cleanup_phase85_20261116/prepare_windows_test.py"),
  str(WORK)],check=True)
p=WORK/"testsrc/l1j/server/server/model/network/core/connection/CSharedBroadcastWindowsTest.java"
s=p.read_text(encoding="utf-8")
helper="""
    /** Frozen oracle input: clone once BEFORE the first recipient is considered. */
    private static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter
            nativeFrozenFrame(byte[] framed) {
        if(framed==null||framed.length<3)
            throw new IllegalArgumentException("framed test input");
        final byte[] snapshot=framed.clone();
        return new l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter(){
            @Override public void write(java.nio.ByteBuffer b) {
                b.put(snapshot,2,snapshot.length-2);
            }
            @Override public int wireLength() {return snapshot.length;}
        };
    }

"""
anchor=re.search(r'(?m)^[ \t]*public static void main\s*\(',s)
assert anchor, "real TCP regression class main not found"
s=s[:anchor.start()]+helper+s[anchor.start():]
replacements=(
("new CAsyncSendConstBuffer(emblemWire,",
 "new CAsyncSendConstBuffer(nativeFrozenFrame(emblemWire),",1),
("new CAsyncSendConstBuffer(frame,",
 "new CAsyncSendConstBuffer(nativeFrozenFrame(frame),",1),
("new CAsyncSendConstBufferExceptSelf(frame,",
 "new CAsyncSendConstBufferExceptSelf(nativeFrozenFrame(frame),",4),
)
for old,new,count in replacements:
    assert s.count(old)==count,(old,s.count(old),count)
    s=s.replace(old,new)
p.write_text(s,encoding="utf-8")
print("PHASE86_NATIVE_ONLY_TEST_ORACLES_REWRITTEN",flush=True)
