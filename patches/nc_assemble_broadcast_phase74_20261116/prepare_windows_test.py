#!/usr/bin/env python3
"""74: append precise byte-for-byte native pooled IOCP encryption tests to 73 suite."""
from pathlib import Path
import subprocess, sys
ROOT = Path(__file__).resolve().parents[2]
work = Path(sys.argv[1]).resolve()
subprocess.run([sys.executable,
    str(ROOT / "patches/nc_assemble_broadcast_phase73_20261116/prepare_windows_test.py"),
    str(work)], check=True)
p = work / "testsrc/l1j/server/server/model/network/core/connection/CSharedBroadcastWindowsTest.java"
s = p.read_text(encoding="utf-8")
anchor = "try(World world=new World()) {"
assert s.count(anchor) == 1
tests = r"""
            // Phase 74: six additional fixed wire formats, POISON 0/1/2
            // branches and independent rolling ciphers on four IOCP sockets.
            final java.lang.reflect.Method p74Format =
                    l1j.server.server.model.network.core.broadcast.CAsyncSendConstBufferExceptSelf.class
                            .getDeclaredMethod("forFormatted", CUserSocket.class,
                                    int.class,int.class,int.class,int.class,
                                    int.class,int.class,int.class);
            p74Format.setAccessible(true);
            final int[][] p74Cases = new int[][] {
                {9,l1j.server.server.Opcodes.S_CHANGE_LIGHT,0x81234567,0xFE,0,0},
                {10,l1j.server.server.Opcodes.S_POISON,0x81234567,0,0,0},
                {10,l1j.server.server.Opcodes.S_POISON,0x81234567,1,0,0},
                {10,l1j.server.server.Opcodes.S_POISON,0x81234567,2,0,0},
                {11,l1j.server.server.Opcodes.S_HIT_RATIO,0x81234567,0xFF,0xFE,0},
                {12,l1j.server.server.Opcodes.S_CHANGE_LEVEL,0x81234567,0xCD,0xAB,0},
                {13,l1j.server.server.Opcodes.S_INVISIBLE,0x81234567,0xFF,0,0},
                {14,l1j.server.server.Opcodes.S_ACTION,0x81234567,0xFE,0x1234,0xABCD}
            };
            for (int ci=0;ci<p74Cases.length;ci++) {
                final int[] v=p74Cases[ci];
                final int len=switch(v[0]) {
                    case 9,13 -> 8; case 10 -> 9; case 11 -> 11;
                    case 12 -> 10; case 14 -> 18;
                    default -> throw new AssertionError("bad phase74 format");
                };
                final java.nio.ByteBuffer expectedBuffer =
                        java.nio.ByteBuffer.allocate(len)
                                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
                expectedBuffer.putShort((short)len).put((byte)v[1]);
                switch(v[0]) {
                    case 9,13 -> expectedBuffer.putInt(v[2]).put((byte)v[3]);
                    case 10 -> expectedBuffer.putInt(v[2])
                            .put((byte)(v[3]==1?1:0)).put((byte)(v[3]==2?1:0));
                    case 11 -> expectedBuffer.putInt(v[2])
                            .put((byte)v[3]).put((byte)v[4]).putShort((short)0);
                    case 12 -> expectedBuffer.putInt(v[2])
                            .put((byte)v[3]).put((byte)v[4]).put((byte)0);
                    case 14 -> expectedBuffer.putInt(v[2]).put((byte)v[3])
                            .putShort((short)v[4]).putShort((short)v[5])
                            .putInt(0).putShort((short)0);
                    default -> throw new AssertionError("invalid case");
                }
                check(expectedBuffer.position()==len,
                        "phase74 legacy byte layout length "+ci);
                final byte[] oracle=expectedBuffer.array();
                try (var nativePacket =
                        (l1j.server.server.model.network.core.broadcast.CAsyncSendConstBufferExceptSelf)
                        p74Format.invoke(null,world.users[0],999,
                                v[0],v[1],v[2],v[3],v[4],v[5])) {
                    check(nativePacket!=null,"phase74 source missing "+ci);
                    for(int k=0;k<4;k++)
                        check(nativePacket.send(world.users[k],100+k),
                                "phase74 enqueue failed "+ci+":"+k);
                }
                for(int k=0;k<4;k++)
                    world.read(k,Arrays.copyOfRange(oracle,2,len),false);
                for(CUserSocket user:world.users)until(user::isWriteIdle);
                System.out.println("PHASE74_NATIVE_FORMAT_"+v[0]+"_FOUR_KEYS_PASS");
            }
            System.out.println("PHASE74_NC_ASSEMBLE_8_ORACLES_32_REAL_IOCP_SEND_PASS");
"""
s = s.replace(anchor, anchor + "\n" + tests, 1)
p.write_text(s, encoding="utf-8")
assert s.count("PHASE74_NC_ASSEMBLE_8_ORACLES_32_REAL_IOCP_SEND_PASS") == 1
print("PHASE74_WINDOWS_8_ORACLES_PREPARED")
