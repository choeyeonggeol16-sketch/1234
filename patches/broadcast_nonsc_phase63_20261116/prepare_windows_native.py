#!/usr/bin/env python3
"""Validate phase62 against the real Windows NIO2/CTransport IOCP pipeline.
Only test harness API alias is updated; game production bytes remain unchanged.
"""
from pathlib import Path
import hashlib,sys,zipfile,re
ROOT=Path(__file__).resolve().parents[2]
ARCHIVE=ROOT/"downloads/RKern_Broadcast_NonSC_Phase62_FULL_SRC_20261116.zip"
EXPECTED_BLOB="556a289819e5f746c3ef0ba217cef54b981274ef"
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
out=work/"testsrc/l1j/server/server/model/network/core/connection/CSharedBroadcastWindowsTest.java"
out.parent.mkdir(parents=True,exist_ok=True)
out.write_text(test,encoding="utf-8")
(work/"sources.txt").write_text("\n".join(str(work/n) for n in names)+"\n",encoding="utf-8")
print("PHASE63_WINDOWS_FIXTURE_PREPARED",len(names),"java","dll_sha256",sha,
      "original_test_lines",len(test.splitlines()))
