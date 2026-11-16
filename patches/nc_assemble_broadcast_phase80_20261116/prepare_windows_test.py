#!/usr/bin/env python3
"""80: validate direct native byte layout of AreaAttack, Chat, GroundItem vs originals."""
from pathlib import Path
import subprocess,sys
ROOT=Path(__file__).resolve().parents[2]
WORK=Path(sys.argv[1]).resolve()
subprocess.run([sys.executable,
    str(ROOT/"patches/nc_assemble_broadcast_phase79_20261116/prepare_windows_test.py"),
    str(WORK)],check=True)
p=WORK/"testsrc/l1j/server/server/model/network/core/connection/CSharedBroadcastWindowsTest.java"
s=p.read_text(encoding="utf-8")
marker="try(World world=new World()) {"
assert s.count(marker)==1
test=r"""
            // Phase80: invoke the ACTUAL production Native slab-writing method,
            // compare against the exact 78/79 legacy SC byte constructor oracle,
            // and transmit with four independent rolling cipher states.
            final var p80Area= l1j.server.server.model.network.core.sc.packet.SC_AreaAttackEffect.class;
            final var p80Drop= l1j.server.server.model.network.core.sc.packet.SC_DropItem.class;
            final var p80Chat= l1j.server.server.model.network.core.sc.packet.SC_ChatPacket.class;
            final java.lang.reflect.Method p80AreaNative=p80Area.getDeclaredMethod(
                "writeNativeArea",java.nio.ByteBuffer.class,
                int.class,int.class,int.class,int.class,int.class,int.class,int.class,int[].class);
            final java.lang.reflect.Method p80DropNative=p80Drop.getDeclaredMethod(
                "writeNativeGroundItem",java.nio.ByteBuffer.class,
                int.class,int.class,int.class,int.class,int.class,int.class,String.class);
            final java.lang.reflect.Method p80DropOld=p80Drop.getDeclaredMethod(
                "sharedWire",int.class,int.class,int.class,int.class,int.class,int.class,String.class);
            final java.lang.reflect.Method p80ChatNative=p80Chat.getDeclaredMethod(
                "writeNativeChat",java.nio.ByteBuffer.class,
                int.class,int.class,int.class,String.class,boolean.class);
            p80AreaNative.setAccessible(true);
            p80DropNative.setAccessible(true);
            p80DropOld.setAccessible(true);
            p80ChatNative.setAccessible(true);
            final java.lang.reflect.Method p80Pooled=
                l1j.server.server.model.network.core.broadcast.CAsyncSendConstBufferExceptSelf.class
                .getDeclaredMethod("forWritten",CUserSocket.class,int.class,
                     l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter.class);
            p80Pooled.setAccessible(true);
            final int[] p80Ids={0x81234567,0,0x01020304};
            final byte[][] p80Expected={
                l1j.server.server.model.network.core.sc.packet.SC_AreaAttackEffect.sharedWire(
                    0x81234567,0x1234,0x5678,7,0xAABBCCDD,1001,0x11,p80Ids),
                (byte[])p80DropOld.invoke(null,0x1234,0x5678,0x81234567,1001,7,12345,"보물 상자 (+3)"),
                l1j.server.server.model.network.core.sc.packet.SC_ChatPacket.sharedWire(
                    l1j.server.server.Opcodes.S_MESSAGE,1,0,"한글 방송",false),
                l1j.server.server.model.network.core.sc.packet.SC_ChatPacket.sharedWire(
                    l1j.server.server.Opcodes.S_SAY,0,0x81234567,"홍길동: 한글 채팅",true)
            };
            final l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter[] p80Writers={
                b->{
                    try{p80AreaNative.invoke(null,b,0x81234567,0x1234,0x5678,7,
                        0xAABBCCDD,1001,0x11,p80Ids);}
                    catch(java.lang.reflect.InvocationTargetException e){throw new IllegalStateException(e.getCause());}
                    catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
                },
                b->{
                    try{p80DropNative.invoke(null,b,0x1234,0x5678,0x81234567,
                        1001,7,12345,"보물 상자 (+3)");}
                    catch(java.lang.reflect.InvocationTargetException e){throw new IllegalStateException(e.getCause());}
                    catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
                },
                b->{
                    try{p80ChatNative.invoke(null,b,l1j.server.server.Opcodes.S_MESSAGE,
                        1,0,"한글 방송",false);}
                    catch(java.lang.reflect.InvocationTargetException e){throw new IllegalStateException(e.getCause());}
                    catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
                },
                b->{
                    try{p80ChatNative.invoke(null,b,l1j.server.server.Opcodes.S_SAY,
                        0,0x81234567,"홍길동: 한글 채팅",true);}
                    catch(java.lang.reflect.InvocationTargetException e){throw new IllegalStateException(e.getCause());}
                    catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
                }
            };
            check(p80Expected.length==p80Writers.length,"phase80 oracle lengths");
            for(int ci=0;ci<p80Expected.length;ci++){
                final byte[] expected=p80Expected[ci];
                check(expected!=null&&expected.length>=4,"phase80 old byte oracle "+ci);
                try(var pooled=(l1j.server.server.model.network.core.broadcast.CAsyncSendConstBufferExceptSelf)
                        p80Pooled.invoke(null,world.users[0],999,p80Writers[ci])){
                    check(pooled!=null,"phase80 Native pooled source missing "+ci);
                    for(int k=0;k<4;k++)
                        check(pooled.send(world.users[k],100+k),"phase80 send "+ci+":"+k);
                }
                for(int k=0;k<4;k++)
                    world.read(k,Arrays.copyOfRange(expected,2,expected.length),false);
                for(CUserSocket user:world.users)until(user::isWriteIdle);
                System.out.println("PHASE80_FINAL_SC_"+ci+"_FOUR_CIPHERS_PASS");
            }
            System.out.println("PHASE80_4_SC_ORACLES_16_REAL_IOCP_SEND_PASS");
"""
s=s.replace(marker,marker+"\n"+test,1)
p.write_text(s,encoding="utf-8")
assert s.count("PHASE80_4_SC_ORACLES_16_REAL_IOCP_SEND_PASS")==1
print("PHASE80_4_SC_ORACLE_TEST_PREPARED")
