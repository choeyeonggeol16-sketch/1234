#!/usr/bin/env python3
"""Phase84 wire parity for new native variable payloads + inherited 83/82/81 real Windows IOCP."""
import sys,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
WORK=Path(sys.argv[1]).resolve()
subprocess.run([sys.executable,str(ROOT/"patches/nc_broadcast_phase83_20261116/prepare_windows_test.py"),str(WORK)],check=True)
p=WORK/"testsrc/l1j/server/server/model/network/core/connection/CSharedBroadcastWindowsTest.java"
s=p.read_text(encoding="utf-8")
marker="try(World world=new World()) {"
assert s.count(marker)==1
block=r"""
            // Phase84: exact old byte frame vs direct Native writer, all
            // trailers/prefixes/MS949 preserve the old packet contract.
            final java.util.function.BiConsumer<
                    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter,byte[]>
                parity84=(writer,expected)->{
                check(writer!=null&&expected!=null,"phase84 null Native SC writer oracle");
                final java.nio.ByteBuffer target=java.nio.ByteBuffer.allocate(16384)
                        .order(java.nio.ByteOrder.LITTLE_ENDIAN);
                target.position(2);writer.write(target);
                int n=target.position();target.putShort(0,(short)n);
                check(java.util.Arrays.equals(
                    java.util.Arrays.copyOf(target.array(),n),expected),
                    "phase84 Native SC byte-oracle mismatch size="+n);
            };
            for(int n:new int[]{0,1,0x11223344,-1}){
                parity84.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.changeAttrWriter(
                        n,n+1,false,true),
                    l1j.server.server.model.network.core.sc.packet.SC_ChangeAttr.broadcastWire(n,n+1,false,true));
                parity84.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.changeAttrWriter(
                        n,n+1,true,false),
                    l1j.server.server.model.network.core.sc.packet.SC_ChangeAttr.broadcastWire(n,n+1,true,false));
            }
            for(String value:new String[]{"","Crown 가나","ABC","아하\n"}) {
                final String[] names84=new String[]{value,"",null,"가나다","王","A","😀"};
                parity84.accept(l1j.server.server.model.network.core.sc.packet.SC_WarStartMessage.nativeWriter(names84),
                    l1j.server.server.model.network.core.sc.packet.SC_WarStartMessage.sharedWire(names84));
                final var data84=l1j.server.server.model.network.core.sc.packet.SC_NewChat.prepare(null,4,3,value,"");
                parity84.accept(l1j.server.server.model.network.core.sc.packet.SC_NewChat.nativeWriter(data84),
                    l1j.server.server.model.network.core.sc.packet.SC_NewChat.sharedWire(data84));
            }
            final var rgb84=l1j.server.MJTemplate.MJSimpleRgb.red();
            for(int n:new int[]{0,1,1510,-1,123456}) {
                parity84.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.iconIndexWriter(n,rgb84,6298,10),
                    l1j.server.server.model.network.core.sc.packet.SC_IconMessage.sharedIndexWire(n,rgb84,6298,10));
            }
            System.out.println("PHASE84_NATIVE_SC_VARIABLE_PARITY_PASS");
"""
s=s.replace(marker,marker+"\n"+block,1)
p.write_text(s,encoding="utf-8")
print("PHASE84_WINDOWS_PARITY_EXTENSION_PREPARED",flush=True)
