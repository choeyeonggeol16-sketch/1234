#!/usr/bin/env python3
"""Validate phase67 against the real Windows NIO2/CTransport IOCP pipeline.
Only test harness API alias is updated; game production bytes remain unchanged.
"""
from pathlib import Path
import hashlib,sys,zipfile,re
ROOT=Path(__file__).resolve().parents[2]
ARCHIVE=ROOT/"downloads/RKern_MoveChar_LazyOwned_FULL_SRC_20261116.zip"
EXPECTED_BLOB="bb88674456ff1eb3169785a6e334449777f53207"
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
# Also send the exact phase64 production emblemWire to four native IOCP peers,
# not only an arbitrary packet payload. Their rolling cipher keys differ.
marker="try(World world=new World()) {"
assert test.count(marker)==1
extra="""
            final java.lang.reflect.Method emblemBuilder =
                    l1j.server.server.model.network.core.sc.packet.SC_ReturnedStat.class
                            .getDeclaredMethod("emblemWire", int.class, int.class);
            emblemBuilder.setAccessible(true);
            byte[] emblemWire = (byte[])emblemBuilder.invoke(null, 0x81234567, 0x11223344);
            check(emblemWire.length==12, "phase64 emblem frame length");
            try (var emblemBroadcast=new CAsyncSendConstBuffer(emblemWire,Integer.MIN_VALUE,Integer.MAX_VALUE)) {
                for(int k=0;k<4;k++)
                    check(emblemBroadcast.send(world.users[k], 100+k), "native emblem shared send "+k);
            }
            for(int k=0;k<4;k++)world.read(k,Arrays.copyOfRange(emblemWire,2,12),false);
            for(CUserSocket user:world.users)until(user::isWriteIdle);
            System.out.println("PHASE64_NATIVE_EMBLEM_4_CIPHERS_PASS");
"""
test=test.replace(marker,marker+"\n"+extra,1)

# Prove the actual phase67 internally owned 17-byte MoveChar frame survives
# four separate Windows IOCP sessions with different rolling cipher keys.
move_extra="""
            final java.lang.reflect.Method moveOwnedFactory =
                    l1j.server.server.model.network.core.broadcast.CAsyncSendConstBufferExceptSelf.class
                            .getDeclaredMethod("forMove", int.class,int.class,int.class,
                                               int.class,int.class,int.class);
            moveOwnedFactory.setAccessible(true);
            byte[] moveWire = l1j.server.server.model.network.core.sc.packet.SC_MoveChar
                    .broadcastWire(0x81234567, 0x1234, 0x5678, 6);
            try (var moveOwned=(CAsyncSendConstBufferExceptSelf)moveOwnedFactory.invoke(null,
                    999, l1j.server.server.Opcodes.S_MOVE_OBJECT, 0x81234567, 0x1234, 0x5678, 6)) {
                for(int k=0;k<4;k++)
                    check(moveOwned.send(world.users[k],100+k),"phase67 native move owned send "+k);
            }
            for(int k=0;k<4;k++)
                world.read(k,Arrays.copyOfRange(moveWire,2,17),false);
            for(CUserSocket user:world.users)until(user::isWriteIdle);
            System.out.println("PHASE67_NATIVE_MOVE_4_CIPHERS_PASS");
"""
test=test.replace(marker,marker+"\n"+move_extra,1)


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
print("PHASE64_WINDOWS_FIXTURE_PREPARED",len(names),"java","dll_sha256",sha,
      "original_test_lines",len(test.splitlines()))
