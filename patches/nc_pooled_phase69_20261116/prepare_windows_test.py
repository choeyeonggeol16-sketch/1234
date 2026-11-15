#!/usr/bin/env python3
"""Reuse verified Windows/IOCP native socket fixture; adapt only factory ABI.

All actual bytes and rolling keys are compared by the existing
CSharedBroadcastWindowsTest against the preserved Java L1 codec oracle.
"""
from pathlib import Path
import subprocess,sys,shutil
ROOT=Path(__file__).resolve().parents[2]
work=Path(sys.argv[1]).resolve()
fixture=work/"phase68-test-fixture"
subprocess.run([sys.executable,str(ROOT/"patches/nc_s_action_phase68_20261116/prepare_windows_native.py"),str(fixture)],check=True)
src=fixture/"testsrc"
shutil.copytree(src,work/"testsrc",dirs_exist_ok=True)
shutil.copyfile(fixture/"CIOPacketCodec.dll",work/"CIOPacketCodec.dll")
test=work/"testsrc/l1j/server/server/model/network/core/connection/CSharedBroadcastWindowsTest.java"
s=test.read_text(encoding="utf-8")
def repl(old,new):
    global s
    assert s.count(old)==1,(old,s.count(old))
    s=s.replace(old,new,1)
repl(
'.getDeclaredMethod("forMove", int.class,int.class,int.class,\n                                               int.class,int.class,int.class);',
'.getDeclaredMethod("forMove", CUserSocket.class,int.class,int.class,int.class,\n                                               int.class,int.class,int.class);'
)
repl(
'moveOwnedFactory.invoke(null,\n                    999,',
'moveOwnedFactory.invoke(null,\n                    world.users[0],999,'
)
repl(
'.getDeclaredMethod("forAction", int.class,int.class,int.class,int.class);',
'.getDeclaredMethod("forAction", CUserSocket.class,int.class,int.class,int.class,int.class);'
)
repl(
'actionOwnedFactory.invoke(null,\n                    999,',
'actionOwnedFactory.invoke(null,\n                    world.users[0],999,'
)
# Source object ownership is now backed by the actual native slab.
repl(
'PHASE68_NATIVE_SACTION_4_CIPHERS_PASS',
'PHASE69_NC_NATIVE_POOLED_SACTION_4_CIPHERS_PASS'
)
repl(
'PHASE67_NATIVE_MOVE_4_CIPHERS_PASS',
'PHASE69_NC_NATIVE_POOLED_MOVE_4_CIPHERS_PASS'
)
test.write_text(s,encoding="utf-8")
names=sorted(p for p in (work/"src").rglob("*.java"))
assert len(names)==1710,len(names)
(work/"sources.txt").write_text("\n".join(map(str,names))+"\n",encoding="utf-8")
print("PHASE69_WINDOWS_FIXTURE_PREPARED",len(names),"CTransport FFM native pooled S packets")
