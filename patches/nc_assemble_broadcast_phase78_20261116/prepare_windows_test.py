#!/usr/bin/env python3
"""78 differential L1 protobuf oracle: original WorldPutBuilder vs pooled Writer."""
from pathlib import Path
import subprocess,sys
ROOT=Path(__file__).resolve().parents[2]
WORK=Path(sys.argv[1]).resolve()
subprocess.run([sys.executable,
    str(ROOT/"patches/nc_assemble_broadcast_phase77_20261116/prepare_windows_test.py"),
    str(WORK)],check=True)
p=WORK/"testsrc/l1j/server/server/model/network/core/connection/CSharedBroadcastWindowsTest.java"
s=p.read_text(encoding="utf-8")
anchor="try(World world=new World()) {"
assert s.count(anchor)==1
test=r"""
            // Final SC_WorldPutObject: compare the *actual unmodified
            // phase77 builder codec* against direct Native slab output.
            // The legacy class exists only in testsrc, never production.
            final java.lang.reflect.Method p78Original=
                l1j.server.server.model.network.core.sc.packet.SC_WorldPutObjectOracle.class
                  .getDeclaredMethod("sharedWireBuilder",
                    l1j.server.server.model.network.core.sc.packet.SC_WorldPutObjectOracle.WorldPutBuilder.class);
            final java.lang.reflect.Method p78Native=
                l1j.server.server.model.network.core.sc.packet.SC_WorldPutObject.class
                  .getDeclaredMethod("writeNativeBuilder",java.nio.ByteBuffer.class,
                    l1j.server.server.model.network.core.sc.packet.SC_WorldPutObject.WorldPutBuilder.class);
            final java.lang.reflect.Method p78Pool=
                l1j.server.server.model.network.core.broadcast.CAsyncSendConstBufferExceptSelf.class
                  .getDeclaredMethod("forWritten",CUserSocket.class,int.class,
                    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter.class);
            p78Original.setAccessible(true);
            p78Native.setAccessible(true);
            p78Pool.setAccessible(true);
            final String[] p78Names={
                "테스트 엔피씨", "", "나무인형 3단계", "긴 이름 test 01 02 03 04 05 06 07 08 09 10"
            };
            final String[] p78Titles={
                "보조 타이틀", "", "경기장 한글", "Window Native pooled protobuf"
            };
            for(int ci=0;ci<p78Names.length;ci++){
                final int id=0x71234567+ci;
                final int xx=0x1234+ci,yy=0x7654-ci;
                final var oldBuilder=new l1j.server.server.model.network.core.sc.packet
                        .SC_WorldPutObjectOracle.WorldPutBuilder()
                        .set_object_id(id).set_x(xx).set_y(yy)
                        .set_h(7).set_action(3).set_sprid(0x1234)
                        .set_name(p78Names[ci]).set_title(p78Titles[ci]);
                final var newBuilder=new l1j.server.server.model.network.core.sc.packet
                        .SC_WorldPutObject.WorldPutBuilder()
                        .set_object_id(id).set_x(xx).set_y(yy)
                        .set_h(7).set_action(3).set_sprid(0x1234)
                        .set_name(p78Names[ci]).set_title(p78Titles[ci]);
                final byte[] expected=(byte[])p78Original.invoke(null,oldBuilder);
                check(expected!=null&&expected.length>12,
                        "phase78 original WorldPut oracle returned no packet "+ci);
                final l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter writer=b->{
                    try {p78Native.invoke(null,b,newBuilder);}
                    catch(java.lang.reflect.InvocationTargetException ex){
                        throw new IllegalStateException("Native WorldPut encoded failure",ex.getCause());
                    } catch(ReflectiveOperationException ex){
                        throw new IllegalStateException("Native WorldPut reflection failure",ex);
                    }
                };
                try(var source=(l1j.server.server.model.network.core.broadcast.CAsyncSendConstBufferExceptSelf)
                        p78Pool.invoke(null,world.users[0],999,writer)){
                    check(source!=null,"phase78 null pooled WorldPut Source "+ci);
                    for(int k=0;k<4;k++)
                        check(source.send(world.users[k],100+k),
                                "phase78 WorldPut SEND refused "+ci+":"+k);
                }
                for(int k=0;k<4;k++)
                    world.read(k,Arrays.copyOfRange(expected,2,expected.length),false);
                for(CUserSocket u:world.users)until(u::isWriteIdle);
                System.out.println("PHASE78_WORLDPUT_BUILDER_ORACLE_"+ci+"_FOUR_CIPHERS_PASS");
            }
            System.out.println("PHASE78_WORLDPUT_4_BUILDERS_16_REAL_IOCP_SEND_PASS");
"""
s=s.replace(anchor,anchor+"\n"+test,1)
p.write_text(s,encoding="utf-8")
assert s.count("PHASE78_WORLDPUT_4_BUILDERS_16_REAL_IOCP_SEND_PASS")==1
assert (WORK/"testsrc/l1j/server/server/model/network/core/sc/packet/SC_WorldPutObjectOracle.java").is_file()
print("PHASE78_WORLDPUT_DIFFERENTIAL_FIXTURE_PREPARED")
