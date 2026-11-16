#!/usr/bin/env python3
"""79: original SC global codec vs pooled Native ByteBuffer on four live IOCP ciphers."""
from pathlib import Path
import subprocess,sys
ROOT=Path(__file__).resolve().parents[2]
WORK=Path(sys.argv[1]).resolve()
subprocess.run([sys.executable,
        str(ROOT/"patches/nc_assemble_broadcast_phase78_20261116/prepare_windows_test.py"),
        str(WORK)],check=True)
p=WORK/"testsrc/l1j/server/server/model/network/core/connection/CSharedBroadcastWindowsTest.java"
s=p.read_text(encoding="utf-8")
anchor="try(World world=new World()) {"
assert s.count(anchor)==1
test=r"""
            // Phase79 original SC packet oracles and Native pooled direct writes.
            // Test isolated original byte[] constructors; production broadcast
            // must never invoke those helpers.
            final java.lang.reflect.Method p79Factory =
                l1j.server.server.model.network.core.broadcast.CAsyncSendConstBufferExceptSelf.class
                .getDeclaredMethod("forWritten", CUserSocket.class,int.class,
                  l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter.class);
            p79Factory.setAccessible(true);
            final java.lang.reflect.Method p79TopOracle=
                l1j.server.server.model.network.core.sc.packet.SC_TopMessage.class
                .getDeclaredMethod("sharedWire",String.class);
            p79TopOracle.setAccessible(true);
            final java.lang.reflect.Method p79WarOracle=
                l1j.server.server.model.network.core.sc.packet.SC_War.class
                .getDeclaredMethod("sharedWire",int.class,String.class,String.class);
            p79WarOracle.setAccessible(true);
            final java.lang.reflect.Method p79WeatherOracle=
                l1j.server.server.model.network.core.sc.packet.SC_Weather.class
                .getDeclaredMethod("sharedWire",int.class);
            p79WeatherOracle.setAccessible(true);
            final java.lang.reflect.Method p79WarWriter=
                l1j.server.server.model.network.core.sc.packet.SC_War.class
                .getDeclaredMethod("writeNativeWar",java.nio.ByteBuffer.class,int.class,
                        String.class,String.class);
            p79WarWriter.setAccessible(true);
            final java.lang.reflect.Method p79ChatWriter=
                l1j.server.server.model.network.core.sc.packet.SC_NewChat.class
                .getDeclaredMethod("writeNativeChat",java.nio.ByteBuffer.class,
                    l1j.server.server.model.network.core.sc.packet.SC_NewChat.ChatData.class);
            p79ChatWriter.setAccessible(true);
            final var p79Rgb=new l1j.server.MJTemplate.MJSimpleRgb(249,68,253);
            final byte[] p79RgbBytes=p79Rgb.get_bytes();
            final String p79Text="한글 공지 2026";
            final var p79ChatData=
                l1j.server.server.model.network.core.sc.packet.SC_NewChat
                    .prepare(null,3,0,"한글 채팅",null);
            final byte[][] p79Oracle={
                l1j.server.server.model.network.core.sc.packet.SC_IconMessage.sharedGmWire("한글 공지"),
                l1j.server.server.model.network.core.sc.packet.SC_IconMessage.sharedIndexWire(3,p79Rgb,28,60),
                l1j.server.server.model.network.core.sc.packet.SC_MessageYN.sharedWire(0x1234,p79Text),
                l1j.server.server.model.network.core.sc.packet.SC_NewChat.sharedWire(p79ChatData),
                l1j.server.server.model.network.core.sc.packet.SC_ServerMessage.sharedWire(208),
                l1j.server.server.model.network.core.sc.packet.SC_SystemMessage.sharedWire(p79Text),
                (byte[])p79TopOracle.invoke(null,p79Text),
                (byte[])p79WarOracle.invoke(null,7,"혈맹 한글","반대 혈맹"),
                (byte[])p79WeatherOracle.invoke(null,0xfe)
            };
            final l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter[] p79Writers={
                b->{
                    b.put((byte)l1j.server.server.Opcodes.S_EXTENDED_PROTOBUF)
                        .putShort((short)0x40).put((byte)0x08).put((byte)56)
                        .put((byte)0x12);
                    l1j.server.server.model.network.core.broadcast.CSharedBroadcast
                        .writeMS949S2(b,"한글 공지");
                    b.put((byte)0x1a).put(p79RgbBytes).put((byte)0x20)
                        .put((byte)60).putShort((short)0);
                },
                b->b.put((byte)l1j.server.server.Opcodes.S_EXTENDED_PROTOBUF)
                    .putShort((short)0x67)
                    .put((byte)0x08).put((byte)56)
                    .put((byte)0x10).put((byte)6)
                    .put((byte)0x1a).put(p79RgbBytes)
                    .put((byte)0x20).put((byte)60).putShort((short)0),
                b->{
                    b.put((byte)l1j.server.server.Opcodes.S_ASK)
                        .putShort((short)0).putInt(0).putShort((short)0x1234);
                    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.writeMS949(b,p79Text);
                    b.put((byte)0);
                },
                b->{
                    try {p79ChatWriter.invoke(null,b,p79ChatData);}
                    catch(java.lang.reflect.InvocationTargetException ex){
                        throw new IllegalStateException("phase79 chat",ex.getCause());
                    } catch(ReflectiveOperationException ex){
                        throw new IllegalStateException(ex);
                    }
                },
                b->b.put((byte)l1j.server.server.Opcodes.S_MESSAGE_CODE)
                    .putShort((short)208).put((byte)0),
                b->{
                    b.put((byte)l1j.server.server.Opcodes.S_MESSAGE).put((byte)0x09);
                    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.writeMS949(b,p79Text);
                    b.put((byte)0);
                },
                b->{
                    b.put((byte)121).put((byte)84).put((byte)2);
                    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.writeMS949(b,p79Text);
                    b.put((byte)0).put((byte)0).put((byte)0);
                },
                b->{
                    try {p79WarWriter.invoke(null,b,7,"혈맹 한글","반대 혈맹");}
                    catch(java.lang.reflect.InvocationTargetException ex){
                        throw new IllegalStateException("phase79 war",ex.getCause());
                    } catch(ReflectiveOperationException ex){
                        throw new IllegalStateException(ex);
                    }
                },
                b->b.put((byte)l1j.server.server.Opcodes.S_WEATHER).put((byte)0xfe)
            };
            check(p79Oracle.length==p79Writers.length,"phase79 test array mismatch");
            for(int ci=0;ci<p79Oracle.length;ci++){
                final byte[] oracle=p79Oracle[ci];
                check(oracle!=null && oracle.length>=4,
                    "phase79 original constructor returned null "+ci);
                try (var packet=(l1j.server.server.model.network.core.broadcast.CAsyncSendConstBufferExceptSelf)
                        p79Factory.invoke(null,world.users[0],999,p79Writers[ci])) {
                    check(packet!=null,"phase79 no native pooled source "+ci);
                    for(int k=0;k<4;k++)
                        check(packet.send(world.users[k],100+k),
                            "phase79 Native global IOCP SEND refused "+ci+":"+k);
                }
                for(int k=0;k<4;k++)
                    world.read(k,Arrays.copyOfRange(oracle,2,oracle.length),false);
                for(CUserSocket u:world.users)until(u::isWriteIdle);
                System.out.println("PHASE79_GLOBAL_SC_ORACLE_"+ci+"_FOUR_KEYS_PASS");
            }
            System.out.println("PHASE79_9_GLOBAL_SC_ORACLES_36_REAL_IOCP_SEND_PASS");
"""
s=s.replace(anchor,anchor+"\n"+test,1)
p.write_text(s,encoding="utf-8")
assert s.count("PHASE79_9_GLOBAL_SC_ORACLES_36_REAL_IOCP_SEND_PASS")==1
print("PHASE79_GLOBAL_SC_ORACLE_TEST_PREPARED")
