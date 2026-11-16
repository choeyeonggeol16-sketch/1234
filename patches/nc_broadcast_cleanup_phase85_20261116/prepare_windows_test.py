#!/usr/bin/env python3
"""Extend 84 Windows regression: test old byte oracles through new Native entrypoints.

The existing oracle still constructs *expected test fixtures* as byte[].
Production CSharedBroadcast.send/sendAll(byte[]) methods were retired in 85.
Only the harness invocation is migrated; bytes, AOI order, encryption checks,
wire assertions, IOCP runtime and output expectations are unchanged.
"""
import sys,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
WORK=Path(sys.argv[1]).resolve()
subprocess.run([sys.executable,
    str(ROOT/"patches/nc_broadcast_phase84_20261116/prepare_windows_test.py"),
    str(WORK)],check=True)
D=WORK/"testsrc/l1j/server/server/model/network/core/sc/packet"
targets={
"SC_WorldPutObjectOracle.java":[
    (".send(npc, frozen, true, -1, null);",
     ".sendWritten(npc,b->b.put(frozen,2,frozen.length-2),true,-1,null);",1),
    (".send(pc, frozen, true, -1, null);",
     ".sendWritten(pc,b->b.put(frozen,2,frozen.length-2),true,-1,null);",1),
    (".send(anchor, frozen, true, -1, null);",
     ".sendWritten(anchor,b->b.put(frozen,2,frozen.length-2),true,-1,null);",1),
],
"SC_TopMessageOracle.java":[
    (".sendAll(wire);",
     ".sendWrittenAll(b->b.put(wire,2,wire.length-2));",1)],
"SC_WeatherOracle.java":[
    (".sendAll(wire);",
     ".sendWrittenAll(b->b.put(wire,2,wire.length-2));",1)]
}
for file,pairs in targets.items():
    p=D/file;s=p.read_text(encoding="utf-8")
    for old,new,expected in pairs:
        n=s.count(old)
        assert n==expected,(file,old,n,expected)
        s=s.replace(old,new)
    p.write_text(s,encoding="utf-8")
print("PHASE85_RETIRED_BYTE_API_TEST_ORACLES_MIGRATED_TO_NATIVE_IOCP",flush=True)
