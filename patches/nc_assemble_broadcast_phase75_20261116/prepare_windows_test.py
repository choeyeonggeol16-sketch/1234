#!/usr/bin/env python3
"""75: 16 fixed SC packet wire-oracles, four independent rolling ciphers per case."""
from pathlib import Path
import subprocess,sys
ROOT=Path(__file__).resolve().parents[2]
WORK=Path(sys.argv[1]).resolve()
subprocess.run([sys.executable,
    str(ROOT/"patches/nc_assemble_broadcast_phase74_20261116/prepare_windows_test.py"),
    str(WORK)],check=True)
p=WORK/"testsrc/l1j/server/server/model/network/core/connection/CSharedBroadcastWindowsTest.java"
s=p.read_text(encoding="utf-8")
anchor="try(World world=new World()) {"
assert s.count(anchor)==1
test=r"""
            // Phase75: 16 extra Native pooled fixed packet schemas are compared
            // against original little-endian L1 wire layouts on four IOCP ciphers.
            final java.lang.reflect.Method p75Formatter =
                    l1j.server.server.model.network.core.broadcast.CAsyncSendConstBufferExceptSelf.class
                    .getDeclaredMethod("forFormatted",CUserSocket.class,
                            int.class,int.class,int.class,int.class,
                            int.class,int.class,int.class);
            p75Formatter.setAccessible(true);
            final int[][] p75Cases=new int[][] {
                {15,l1j.server.server.Opcodes.S_CHANGE_ATTR,0x1234,0x5678,0xFE,0x81},
                {16,l1j.server.server.Opcodes.S_CHANGE_ALIGNMENT,0x81234567,0xCAFE,0,0},
                {17,l1j.server.server.Opcodes.S_DRUNKEN,0x81234567,0xFE,0,0},
                {18,l1j.server.server.Opcodes.S_CRIMINAL,0x81234567,0xFE,0,0},
                {19,l1j.server.server.Opcodes.S_EMOTION,0x81234567,0xFD,0xCDEF,0},
                {20,l1j.server.server.Opcodes.S_SPEED,0x81234567,0xFE,0xABCD,0},
                {21,l1j.server.server.Opcodes.S_EVENT,0x16,0xE3,0xFEDC,0},
                {22,l1j.server.server.Opcodes.S_BREATH,0x81234567,0xCAFE,0,0},
                {23,l1j.server.server.Opcodes.S_MAGE_SHIELD,0xCDEF,0xFE,0,0},
                {24,l1j.server.server.Opcodes.S_EVENT,0x81234567,0,0,0},
                {25,l1j.server.server.Opcodes.S_EVENT,0x39,0x2c,0xABCD,0},
                {26,l1j.server.server.Opcodes.S_RESURRECT,0x81234567,0xFD,0x76543210,0x83ABCDEF},
                {27,l1j.server.server.Opcodes.S_MAGE_DEXTERITY,0xCAFE,0xFE,0xAB,0},
                {28,l1j.server.server.Opcodes.S_MAGE_STRENGTH,0xCAFE,0xFE,0xAB,0xCD},
                {29,l1j.server.server.Opcodes.S_WIELD,0x81234567,0xFE,0,0},
                {30,l1j.server.server.Opcodes.S_EVENT,0x81234567,1,0,0}
            };
            for(int ci=0;ci<p75Cases.length;ci++){
                final int[] v=p75Cases[ci];
                final int len=switch(v[0]){
                    case 15,22 -> 9; case 16 -> 13; case 17,18 -> 8;
                    case 19,20,23 -> 10; case 21,25 -> 7;
                    case 24,27 -> 11; case 26 -> 16;
                    case 28,29 -> 12; case 30 -> 18;
                    default -> throw new AssertionError("bad phase75 kind");
                };
                final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(len)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN);
                b.putShort((short)len).put((byte)v[1]);
                switch(v[0]){
                    case 15 -> b.putShort((short)v[2]).putShort((short)v[3])
                                   .put((byte)v[4]).put((byte)v[5]);
                    case 16 -> b.putInt(v[2]).putShort((short)v[3]).putInt(0);
                    case 17,18 -> b.putInt(v[2]).put((byte)v[3]);
                    case 19,20 -> b.putInt(v[2]).put((byte)v[3]).putShort((short)v[4]);
                    case 21,25 -> b.put((byte)v[2]).put((byte)v[3]).putShort((short)v[4]);
                    case 22 -> b.putInt(v[2]).putShort((short)v[3]);
                    case 23 -> b.putShort((short)v[2]).put((byte)v[3]).putInt(0);
                    case 24 -> b.put((byte)0x2c).putInt(v[2])
                                     .put((byte)0).put((byte)0x0a).put((byte)0x80);
                    case 26 -> b.putInt(v[2]).put((byte)v[3]).putInt(v[4]).putInt(v[5]);
                    case 27 -> b.putShort((short)v[2]).put((byte)v[3])
                                     .put((byte)v[4]).putInt(0);
                    case 28 -> b.putShort((short)v[2]).put((byte)v[3])
                                     .put((byte)v[4]).put((byte)v[5]).putInt(0);
                    case 29 -> b.putInt(v[2]).put((byte)v[3]).put((byte)0xff)
                                     .put((byte)0xff).putShort((short)0);
                    case 30 -> b.put((byte)0xc2).putInt(v[2]).put((byte)0x4f)
                                     .put((byte)0x33).put((byte)0).put((byte)0)
                                     .putInt(v[3]).putShort((short)0);
                    default -> throw new AssertionError("unexpected phase75 kind");
                }
                check(b.position()==len,"phase75 packet length mismatch "+ci);
                final byte[] expected=b.array();
                try(var pooled=(l1j.server.server.model.network.core.broadcast.CAsyncSendConstBufferExceptSelf)
                        p75Formatter.invoke(null,world.users[0],999,
                                v[0],v[1],v[2],v[3],v[4],v[5])) {
                    check(pooled!=null,"phase75 pooled source missing "+ci);
                    for(int k=0;k<4;k++)
                        check(pooled.send(world.users[k],100+k),
                                "phase75 send failed "+ci+":"+k);
                }
                for(int k=0;k<4;k++)
                    world.read(k,Arrays.copyOfRange(expected,2,len),false);
                for(CUserSocket u:world.users)until(u::isWriteIdle);
                System.out.println("PHASE75_NATIVE_FIXED_KIND_"+v[0]+"_FOUR_CIPHERS_PASS");
            }
            System.out.println("PHASE75_16_FIXED_SC_64_REAL_IOCP_SEND_PASS");
"""
s=s.replace(anchor,anchor+"\n"+test,1)
p.write_text(s,encoding="utf-8")
assert s.count("PHASE75_16_FIXED_SC_64_REAL_IOCP_SEND_PASS")==1
print("PHASE75_16_FIXED_ORACLES_PREPARED")
