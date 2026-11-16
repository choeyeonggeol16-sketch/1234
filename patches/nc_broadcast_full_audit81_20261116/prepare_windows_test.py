#!/usr/bin/env python3
"""Audit81: adversarial 16KiB+ boundaries on the existing Windows IOCP fixture."""
from pathlib import Path
import subprocess,sys
ROOT=Path(__file__).resolve().parents[2]
WORK=Path(sys.argv[1]).resolve()
subprocess.run([sys.executable,
    str(ROOT/"patches/nc_assemble_broadcast_phase80_20261116/prepare_windows_test.py"),
    str(WORK)],check=True)
p=WORK/"testsrc/l1j/server/server/model/network/core/connection/CSharedBroadcastWindowsTest.java"
s=p.read_text(encoding="utf-8")
marker="try(World world=new World()) {"
assert s.count(marker)==1
test=r"""
            // Audit81: reproduce the former false-negative ground item sizing.
            final var dropClazz=l1j.server.server.model.network.core.sc.packet.SC_DropItem.class;
            final var dropFit=dropClazz.getDeclaredMethod("fitsNativeGroundItemWire",String.class);
            dropFit.setAccessible(true);
            final String longAscii="A".repeat(9000);
            final String longKorean="가".repeat(9000);
            check((boolean)dropFit.invoke(null,longAscii),"audit81 false drop of 9k ASCII name");
            check(!(boolean)dropFit.invoke(null,longKorean),"audit81 oversized Korean item not rejected");
            check((boolean)dropFit.invoke(null,"가".repeat(8169)),
                    "audit81 should admit exact 16383-byte Korean ground item");
            check(!(boolean)dropFit.invoke(null,"가".repeat(8170)),
                    "audit81 should reject 16385-byte Korean ground item");
            final var dropWire=dropClazz.getDeclaredMethod("sharedWire",
                int.class,int.class,int.class,int.class,int.class,int.class,String.class);
            dropWire.setAccessible(true);
            final byte[] oldAscii=(byte[])dropWire.invoke(null,1,2,3,4,5,6,longAscii);
            check(oldAscii!=null&&oldAscii.length==9045,
                    "audit81 original ground item ASCII oracle");
            System.out.println("AUDIT81_ITEM_MS949_BOUNDARIES_PASS");

            final var iconClazz=l1j.server.server.model.network.core.sc.packet.SC_IconMessage.class;
            final var iconFit=iconClazz.getDeclaredMethod("fitsTextBroadcast",String.class,int.class);
            iconFit.setAccessible(true);
            check((boolean)iconFit.invoke(null,"A".repeat(255),3),
                    "audit81 255-byte ASCII icon false negative");
            check(!(boolean)iconFit.invoke(null,"A".repeat(256),3),
                    "audit81 256-byte ASCII icon not refused");
            check((boolean)iconFit.invoke(null,"가".repeat(127),3),
                    "audit81 254-byte MS949 icon false negative");
            check(!(boolean)iconFit.invoke(null,"가".repeat(128),3),
                    "audit81 256-byte MS949 icon not refused");
            System.out.println("AUDIT81_ICON_S2_255_BOUNDARIES_PASS");

            // Audit81: force the exact NEW CSharedBroadcast direct CIO FIFO
            // large-writer through the existing real 4-key Windows IOCP world.
            final var largeMethod=
                l1j.server.server.model.network.core.broadcast.CSharedBroadcast.class
                .getDeclaredMethod("sendLargeDirectFrame",CUserSocket.class,int.class,
                    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter.class);
            largeMethod.setAccessible(true);
            for(final int bytes:new int[]{16385,32768,65535}){
                final byte[] plain=new byte[bytes-2];
                for(int i=0;i<plain.length;i++)plain[i]=(byte)(i*29+17);
                final boolean admitted=(boolean)largeMethod.invoke(null,
                    world.users[0],bytes,
                    (l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter)
                            b->b.put(plain));
                check(admitted,"audit81 large direct admission "+bytes);
                world.read(0,plain,false);
                until(world.users[0]::isWriteIdle);
                System.out.println("AUDIT81_NATIVE_LARGE_DIRECT_"+bytes+"_CIPHER_PASS");
            }
            System.out.println("AUDIT81_THREE_BOUNDARY_FIXES_REAL_IOCP_PASS");
"""
s=s.replace(marker,marker+"\n"+test,1)
p.write_text(s,encoding="utf-8")
assert s.count("AUDIT81_THREE_BOUNDARY_FIXES_REAL_IOCP_PASS")==1
print("AUDIT81_ADVERSARIAL_BOUNDARY_TEST_PREPARED",flush=True)
