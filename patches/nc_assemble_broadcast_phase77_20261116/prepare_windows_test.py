#!/usr/bin/env python3
"""Phase77 real Windows IOCP four-rolling-cipher writable source regression."""
from pathlib import Path
import subprocess, sys
ROOT=Path(__file__).resolve().parents[2]
WORK=Path(sys.argv[1]).resolve()
subprocess.run([sys.executable,
        str(ROOT/"patches/nc_assemble_broadcast_phase76_20261116/prepare_windows_test.py"),
        str(WORK)],check=True)
p=WORK/"testsrc/l1j/server/server/model/network/core/connection/CSharedBroadcastWindowsTest.java"
s=p.read_text(encoding="utf-8")
anchor="try(World world=new World()) {"
assert s.count(anchor)==1
test=r"""
            // Phase77 mutable CIOBufferEx: a single producer source must be
            // completely assembled and sealed before each independent cipher
            // consumes it. Compare original L1 wire oracles, not just counts.
            final java.lang.reflect.Method p77Factory =
                l1j.server.server.model.network.core.broadcast.CAsyncSendConstBufferExceptSelf.class
                    .getDeclaredMethod("forWritten", CUserSocket.class,int.class,
                        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter.class);
            p77Factory.setAccessible(true);
            final java.nio.charset.Charset p77Ms949 =
                    java.nio.charset.Charset.forName("MS949");
            final String p77Korean = "가나다한글?"; // prove exact MS949 text
            final byte[] p77Text=p77Korean.getBytes(p77Ms949);
            final byte[] p77ShopRaw={(byte)0x80,(byte)0x00,(byte)0xfe};
            final int[] p77Ids={0x81234567,0x76543210};
            final java.nio.ByteBuffer p77Name=java.nio.ByteBuffer
                    .allocate(8+p77Text.length).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            p77Name.putShort((short)p77Name.capacity())
                   .put((byte)l1j.server.server.Opcodes.S_CHANGE_DESC)
                   .putInt(0x81234567).put(p77Text).put((byte)0);
            final java.nio.ByteBuffer p77Shop=java.nio.ByteBuffer
                    .allocate(8+p77ShopRaw.length).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            p77Shop.putShort((short)p77Shop.capacity())
                   .put((byte)l1j.server.server.Opcodes.S_ACTION)
                   .putInt(0x81234567).put((byte)0xf5).put(p77ShopRaw);
            final java.nio.ByteBuffer p77ShopText=java.nio.ByteBuffer
                    .allocate(10+2*2).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            p77ShopText.putShort((short)p77ShopText.capacity())
                   .put((byte)l1j.server.server.Opcodes.S_ACTION)
                   .putInt(0x81234567).put((byte)0xf5)
                   .putChar('가').putChar('나').putShort((short)0);
            final java.nio.ByteBuffer p77Chat=java.nio.ByteBuffer
                    .allocate(9+p77Text.length).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            p77Chat.putShort((short)p77Chat.capacity())
                   .put((byte)l1j.server.server.Opcodes.S_SAY_CODE)
                   .put((byte)21).putInt(0x81234567).put(p77Text).put((byte)0);
            final java.nio.ByteBuffer p77Range=java.nio.ByteBuffer
                    .allocate(24+6*p77Ids.length).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            p77Range.putShort((short)p77Range.capacity())
                    .put((byte)l1j.server.server.Opcodes.S_ATTACK_MANY)
                    .put((byte)0xfd).putInt(0x81234567)
                    .putShort((short)0x1234).putShort((short)0xfedc)
                    .put((byte)0x7).putInt(0x76543210)
                    .putShort((short)0xabcd).put((byte)0x8)
                    .putShort((short)0).putShort((short)p77Ids.length);
            for(int id:p77Ids)p77Range.putInt(id).put((byte)32).put((byte)0xfd);
            final java.nio.ByteBuffer p77Emblem=java.nio.ByteBuffer
                    .allocate(12).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            p77Emblem.putShort((short)12)
                    .put((byte)l1j.server.server.Opcodes.S_VOICE_CHAT)
                    .put((byte)0x3c).putInt(0x81234567).putInt(0xfedcba98);
            final java.nio.ByteBuffer p77Green=java.nio.ByteBuffer
                    .allocate(6+p77Text.length).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            p77Green.putShort((short)p77Green.capacity())
                    .put((byte)l1j.server.server.Opcodes.S_EVENT)
                    .put((byte)0).put((byte)2).put(p77Text).put((byte)0);
            final byte[][] p77Oracles={
                    p77Name.array(),p77Shop.array(),p77ShopText.array(),
                    p77Chat.array(),p77Range.array(),p77Emblem.array()
            };
            final l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter[] p77Writers={
                    b->{b.put((byte)l1j.server.server.Opcodes.S_CHANGE_DESC)
                        .putInt(0x81234567);
                        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.writeMS949(b,p77Korean);
                        b.put((byte)0);},
                    b->{b.put((byte)l1j.server.server.Opcodes.S_ACTION)
                        .putInt(0x81234567).put((byte)0xf5).put(p77ShopRaw);},
                    b->{b.put((byte)l1j.server.server.Opcodes.S_ACTION)
                        .putInt(0x81234567).put((byte)0xf5)
                        .putChar('가').putChar('나').putShort((short)0);},
                    b->{b.put((byte)l1j.server.server.Opcodes.S_SAY_CODE)
                        .put((byte)21).putInt(0x81234567);
                        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.writeMS949(b,p77Korean);
                        b.put((byte)0);},
                    b->{b.put((byte)l1j.server.server.Opcodes.S_ATTACK_MANY)
                        .put((byte)0xfd).putInt(0x81234567)
                        .putShort((short)0x1234).putShort((short)0xfedc)
                        .put((byte)0x7).putInt(0x76543210)
                        .putShort((short)0xabcd).put((byte)0x8)
                        .putShort((short)0).putShort((short)p77Ids.length);
                        for(int id:p77Ids)b.putInt(id).put((byte)32).put((byte)0xfd);},
                    b->{b.put((byte)l1j.server.server.Opcodes.S_VOICE_CHAT)
                        .put((byte)0x3c).putInt(0x81234567).putInt(0xfedcba98);}
            };
            for(int ci=0;ci<p77Writers.length;ci++){
                final byte[] oracle=p77Oracles[ci];
                try (var source=(l1j.server.server.model.network.core.broadcast.CAsyncSendConstBufferExceptSelf)
                        p77Factory.invoke(null,world.users[0],999,p77Writers[ci])) {
                    check(source!=null,"phase77 Native writable pool returned null "+ci);
                    for(int k=0;k<4;k++)
                        check(source.send(world.users[k],100+k),
                                "phase77 native writable send refused "+ci+":"+k);
                }
                for(int k=0;k<4;k++)
                    world.read(k,Arrays.copyOfRange(oracle,2,oracle.length),false);
                for(CUserSocket u:world.users)until(u::isWriteIdle);
                System.out.println("PHASE77_NATIVE_WRITABLE_ORACLE_"+ci+"_FOUR_CIPHERS_PASS");
            }
            // Invalid buffer growth: never publish a partly encoded source.
            try {
                p77Factory.invoke(null,world.users[0],999,
                        (l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter)
                        b->{b.position(16383);b.putInt(0x12345678);});
                throw new AssertionError("writable source overflow must fail");
            } catch(java.lang.reflect.InvocationTargetException expected) {
                check(expected.getCause()!=null,"phase77 missing underlying error");
            }
            System.out.println("PHASE77_NATIVE_WRITABLE_OVERFLOW_RELEASE_PASS");
            System.out.println("PHASE77_NATIVE_WRITABLE_6_ORACLES_24_IOCP_SEND_PASS");
"""
s=s.replace(anchor,anchor+"\n"+test,1)
p.write_text(s,encoding="utf-8")
assert s.count("PHASE77_NATIVE_WRITABLE_6_ORACLES_24_IOCP_SEND_PASS")==1
print("PHASE77_NATIVE_WRITABLE_TEST_PREPARED")
