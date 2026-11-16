#!/usr/bin/env python3
"""76: verify all attack-mode Native pooled wire bytes over four real IOCP ciphers."""
from pathlib import Path
import subprocess,sys
ROOT=Path(__file__).resolve().parents[2]
WORK=Path(sys.argv[1]).resolve()
subprocess.run([sys.executable,
    str(ROOT/"patches/nc_assemble_broadcast_phase75_20261116/prepare_windows_test.py"),
    str(WORK)],check=True)
p=WORK/"testsrc/l1j/server/server/model/network/core/connection/CSharedBroadcastWindowsTest.java"
s=p.read_text(encoding="utf-8")
marker="try(World world=new World()) {"
assert s.count(marker)==1
test=r"""
            // Extended L1 NC pooled Assemble: 13 primitive FFM arguments,
            // no heap packet array or recipient-specific serializer in server.
            final java.lang.reflect.Method p76Build =
                    l1j.server.server.model.network.core.broadcast.CAsyncSendConstBufferExceptSelf.class
                    .getDeclaredMethod("forExtended",CUserSocket.class,
                            int.class,int.class,int.class,
                            int.class,int.class,int.class,int.class,int.class,int.class,
                            int.class,int.class,int.class,int.class,int.class);
            p76Build.setAccessible(true);
            final int[][] p76Cases=new int[][]{
                {31,l1j.server.server.Opcodes.S_POLYMORPH,0x81234567,0xCAFE,0xAB,0,0,0,0,0,0,0,0},
                {32,l1j.server.server.Opcodes.S_ATTACK,0x81234567,0x76543210,0xFE,0xCD,0,0,0,0,0,0,0},
                {33,l1j.server.server.Opcodes.S_ATTACK,0xFD,0x81234567,0x76543210,0xCD,0,0,0,0,0,0,0},
                {34,l1j.server.server.Opcodes.S_ATTACK,0x81234567,0xFE,0x76543210,0xCD,0,0,0,0,0,0,0},
                {35,l1j.server.server.Opcodes.S_ATTACK,0x81234567,0x76543210,0xCAFE,0xFA,0xFEDCBA98,0xCDEF,0xABCD,0xFEDC,0x1234,0x5678,0},
                {36,l1j.server.server.Opcodes.S_ATTACK,0x81234567,0x76543210,0xFE,0xCDEF,0,0,0,0,0,0,0},
                {37,l1j.server.server.Opcodes.S_ATTACK,0x81234567,0x76543210,0xFE,0xD7,1,0xFEDCBA98,0xCDEF,0x1234,0x5678,0xABCD,0xFEDC},
                {37,l1j.server.server.Opcodes.S_ATTACK,0x81234567,0x76543210,0xFE,0xD7,0,0xFEDCBA98,0xCDEF,0x1234,0x5678,0xABCD,0xFEDC},
                {38,l1j.server.server.Opcodes.S_ATTACK,0,0x81234567,0x76543210,0xFE,0xCD,0xAB,0xCDEF,0,0,0,0},
                {39,l1j.server.server.Opcodes.S_ATTACK,0,0x81234567,0x76543210,0xFE,0xCD,0xAB,0xCDEF,0,0,0,0},
                {40,l1j.server.server.Opcodes.S_ATTACK,0,0x81234567,0x76543210,0xFE,0xCD,0xAB,0xCDEF,0,0,0,0},
                {41,l1j.server.server.Opcodes.S_ATTACK,0x81234567,0xABCD,0xFEDC,0xFE,0xCA,0x76543210,0xCDEF,0x1234,0x5678,1,0xFEDCBA98},
                {41,l1j.server.server.Opcodes.S_ATTACK,0x81234567,0xABCD,0xFEDC,0xFE,0xCA,0x76543210,0xCDEF,0x1234,0x5678,0,0xFEDCBA98},
                {42,l1j.server.server.Opcodes.S_ATTACK,0xCA,0x81234567,0x76543210,6,0xFE,0xFEDCBA98,0xCDEF,0xABCD,0xFEDC,0x1234,0x5678}
            };
            for(int ci=0;ci<p76Cases.length;ci++){
                final int[] v=p76Cases[ci];
                final int kind=v[0];
                final int len=switch(kind){
                    case 31 -> 18;case 32,33,38,39 -> 20;
                    case 34 -> 19;case 35 -> 39;
                    case 36,40 -> 26;case 37 -> 34;
                    case 41,42 -> 35;
                    default -> throw new AssertionError("bad phase76 kind");
                };
                final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(len)
                        .order(java.nio.ByteOrder.LITTLE_ENDIAN);
                b.putShort((short)len).put((byte)v[1]);
                switch(kind) {
                    case 31 -> b.putInt(v[2]).putShort((short)v[3])
                            .put((byte)v[4]).put((byte)0xff).put((byte)0xff).put((byte)0)
                            .put((byte)'a').put((byte)'b').put((byte)'c').put((byte)'d')
                            .put((byte)0);
                    case 32 -> b.put((byte)v[4]).putInt(v[2]).putInt(v[3])
                            .put((byte)0).put((byte)0).put((byte)v[5])
                            .putInt(0).put((byte)0);
                    case 33 -> b.put((byte)v[2]).putInt(v[3]).putInt(v[4])
                            .putShort((short)1).put((byte)v[5])
                            .putShort((short)0).putShort((short)0).put((byte)0);
                    case 34 -> b.put((byte)v[5]).putInt(v[2]).putInt(v[4])
                            .put((byte)1).put((byte)0).put((byte)v[3]).putInt(0);
                    case 35 -> b.put((byte)1).putInt(v[2]).putInt(v[3])
                            .putShort((short)v[4]).put((byte)v[5]).putInt(v[6])
                            .putShort((short)v[7]).put((byte)0)
                            .putShort((short)v[8]).putShort((short)v[9])
                            .putShort((short)v[10]).putShort((short)v[11])
                            .putInt(0).putShort((short)0).put((byte)0).putShort((short)0);
                    case 36 -> b.put((byte)1).putInt(v[2]).putInt(v[3])
                            .putShort((short)1).put((byte)v[4])
                            .putShort((short)0).putShort((short)0)
                            .put((byte)2).putInt(v[5]).putShort((short)0);
                    case 37 -> b.put((byte)v[5]).putInt(v[2]).putInt(v[3])
                            .put((byte)(v[6]!=0?6:0)).put((byte)0).put((byte)v[4])
                            .putInt(v[7]).putShort((short)v[8]).put((byte)0)
                            .putShort((short)v[9]).putShort((short)v[10])
                            .putShort((short)v[11]).putShort((short)v[12])
                            .put((byte)0).put((byte)0).put((byte)0).put((byte)0);
                    case 38 -> b.put((byte)v[5]).putInt(v[4]).putInt(v[3])
                            .put((byte)0x4e).put((byte)0).put((byte)v[6])
                            .putInt(0).put((byte)0);
                    case 39 -> b.put((byte)v[5]).putInt(v[3]).putInt(v[4])
                            .putShort((short)2).put((byte)v[6])
                            .putShort((short)0).putShort((short)0).put((byte)v[7]);
                    case 40 -> b.put((byte)v[5]).putInt(v[3]).putInt(v[4])
                            .put((byte)1).put((byte)0).put((byte)v[6])
                            .putShort((short)0).putShort((short)0).put((byte)v[7])
                            .putShort((short)v[8]).putShort((short)0).putShort((short)0);
                    case 41 -> b.put((byte)v[6]).putInt(v[2]).putInt(v[7])
                            .put((byte)(v[11]!=0?6:0)).put((byte)0)
                            .put((byte)v[5]).putInt(v[12])
                            .putShort((short)v[8]).put((byte)0)
                            .putShort((short)v[3]).putShort((short)v[4])
                            .putShort((short)v[9]).putShort((short)v[10])
                            .put((byte)0).put((byte)0).put((byte)0).put((byte)0).put((byte)0);
                    case 42 -> b.put((byte)v[2]).putInt(v[3]).putInt(v[4])
                            .put((byte)v[5]).put((byte)0).put((byte)v[6])
                            .putInt(v[7]).putShort((short)v[8]).put((byte)6)
                            .putShort((short)v[9]).putShort((short)v[10])
                            .putShort((short)v[11]).putShort((short)v[12])
                            .put((byte)0).put((byte)0).put((byte)0).put((byte)0).put((byte)0);
                    default -> throw new AssertionError("bad phase76 kind");
                }
                check(b.position()==len,"phase76 legacy attack packet length mismatch "+ci);
                final byte[] oracle=b.array();
                try(var source=(l1j.server.server.model.network.core.broadcast.CAsyncSendConstBufferExceptSelf)
                        p76Build.invoke(null,world.users[0],999,
                                v[0],v[1],v[2],v[3],v[4],v[5],v[6],
                                v[7],v[8],v[9],v[10],v[11],v[12])) {
                    check(source!=null,"phase76 pooled attack source missing "+ci);
                    for(int k=0;k<4;k++)
                        check(source.send(world.users[k],100+k),
                                "phase76 enqueue failed "+ci+":"+k);
                }
                for(int k=0;k<4;k++)
                    world.read(k,Arrays.copyOfRange(oracle,2,len),false);
                for(CUserSocket u:world.users)until(u::isWriteIdle);
                System.out.println("PHASE76_NATIVE_ATTACK_KIND_"+kind+"_FOUR_CIPHERS_PASS");
            }
            System.out.println("PHASE76_NC_COMBAT_14_ORACLES_56_REAL_IOCP_SEND_PASS");
"""
s=s.replace(marker,marker+"\n"+test,1)
p.write_text(s,encoding="utf-8")
assert s.count("PHASE76_NC_COMBAT_14_ORACLES_56_REAL_IOCP_SEND_PASS")==1
print("PHASE76_COMBAT_ORACLES_PREPARED", len(list((WORK/"src").rglob("*.java"))))
