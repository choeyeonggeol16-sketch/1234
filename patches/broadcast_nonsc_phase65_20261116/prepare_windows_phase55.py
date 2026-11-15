#!/usr/bin/env python3
"""Validate phase55 against the real Windows NIO2/CTransport IOCP pipeline.
Only test harness API alias is updated; game production bytes remain unchanged.
"""
from pathlib import Path
import hashlib,sys,zipfile,re
ROOT=Path(__file__).resolve().parents[2]
ARCHIVE=ROOT/"downloads/RKern_Broadcast_NonSC_Phase55_FULL_SRC_20261116.zip"
EXPECTED_BLOB="af668a28f53bd611ed196e7c4b739243ba11798b"
src=ARCHIVE.read_bytes()
gitblob=hashlib.sha1(b"blob "+str(len(src)).encode()+b"\x00"+src).hexdigest()
assert gitblob==EXPECTED_BLOB,(gitblob,EXPECTED_BLOB)
work=Path(sys.argv[1])
work.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(ARCHIVE) as z:
    assert z.testzip() is None
    names=[n for n in z.namelist() if n.endswith(".java") and n.startswith("src/")]
    assert len(names)==1710,len(names)
    for path in names:
        dst=work/path
        dst.parent.mkdir(parents=True,exist_ok=True)
        dst.write_bytes(z.read(path))
    dll=z.read("CTransport.dll")
    (work/"CTransport.dll").write_bytes(dll)
    sha=hashlib.sha256(dll).hexdigest()
    config=z.read("src/l1j/server/server/model/network/core/config/CIOConfig.java").decode("utf-8")
    assert sha in config,("CIOConfig DLL checksum differs",sha)
test=(ROOT/"patches/ctransport_core_20261112/tests/CSharedBroadcastWindowsTest.java").read_text(encoding="utf-8")
# The old test used CAsyncSendConstBuffer.apply; the new production name is send.
assert test.count(".apply(")==6,test.count(".apply(")
test=test.replace(".apply(", ".send(")
test=test.replace("CIOThreadCallback.INSTANCE","CIOThreadCallback.getInstance()")
test=test.replace("new CIOClient(user)","new CIOClient(user,false)")
# Old .apply was an operation-status predicate even for excluded recipients.
# New .send returns false for ExceptSelf exclusion: that is the correct result.
old='for(int i=0;i<4;i++)check(broadcast.send(world.users[i],i==3?999:100+i),"shared broadcast unavailable");'
new='for(int i=0;i<4;i++)check(broadcast.send(world.users[i],i==3?999:100+i)==(i!=3),"shared broadcast inclusion/exclusion incorrect");'
assert test.count(old)==1, "historical inclusion assertion changed"
test=test.replace(old,new,1)
# CIO game's long-lived owner shards are intentionally non-daemon.
# End the standalone test JVM only AFTER all World.close() lease assertions.
suffix="    }\n}\n"
assert test.endswith(suffix)
test=test[:-len(suffix)]+"        System.exit(0);\n"+suffix

# Test-only native codec reference and its DLL are needed for
# independent per-socket ciphertext comparison.
codec_src=ROOT/"patches/ctransport_core_20261112/tests/CIOPacketCodecReference.java"
codec_dst=work/"testsrc/l1j/server/server/model/network/core/transport/CIOPacketCodecReference.java"
codec_dst.parent.mkdir(parents=True,exist_ok=True)
codec_dst.write_bytes(codec_src.read_bytes())
codec_dll=ROOT/"patches/ctransport_core_20261112/build/CIOPacketCodec.dll"
assert codec_dll.is_file()
(work/"CIOPacketCodec.dll").write_bytes(codec_dll.read_bytes())
out=work/"testsrc/l1j/server/server/model/network/core/connection/CSharedBroadcastWindowsTest.java"
out.parent.mkdir(parents=True,exist_ok=True)
out.write_text(test,encoding="utf-8")
(work/"sources.txt").write_text("\n".join(str(work/n) for n in names)+"\n",encoding="utf-8")
print("PHASE65_WINDOWS_FIXTURE_PREPARED",len(names),"java","dll_sha256",sha,
      "original_test_lines",len(test.splitlines()))
