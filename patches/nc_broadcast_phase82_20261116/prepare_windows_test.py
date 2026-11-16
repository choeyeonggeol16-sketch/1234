#!/usr/bin/env python3
"""Extend phase81 real Windows IOCP regression with phase82 byte parity and Native source fanout."""
import sys,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
WORK=Path(sys.argv[1]).resolve()
subprocess.run([sys.executable,
   str(ROOT/"patches/nc_broadcast_full_audit81_20261116/prepare_windows_test.py"),
   str(WORK)],check=True)
p=WORK/"testsrc/l1j/server/server/model/network/core/connection/CSharedBroadcastWindowsTest.java"
s=p.read_text(encoding="utf-8")
marker="try(World world=new World()) {"
assert s.count(marker)==1
block=r"""
            // Phase82: wire oracles are the previous exact byte[] serializer,
            // but the implementation under test composes directly into
            // the Native slab (we simulate slab memory here for byte diff).
            final java.util.function.BiConsumer<
                    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter,byte[]>
                parity=(writer,legacy)->{
                    check(legacy!=null,"phase82 oracle is null");
                    final java.nio.ByteBuffer buf=java.nio.ByteBuffer.allocate(16384)
                            .order(java.nio.ByteOrder.LITTLE_ENDIAN);
                    buf.position(2);
                    writer.write(buf);
                    final int n=buf.position();
                    buf.putShort(0,(short)n);
                    final byte[] actual=java.util.Arrays.copyOf(buf.array(),n);
                    check(java.util.Arrays.equals(actual,legacy),
                         "phase82 Native writer differs from legacy oracle, length="+n);
                };
            final var br=l1j.server.server.model.network.core.broadcast.CSharedBroadcast.class;
            final String[] examples={"","hello","가나다 ABC","ß 😃","a".repeat(140)};
            for(String example:examples){
                parity.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.greenWriter(example),
                    l1j.server.server.model.network.core.sc.packet.SC_PacketBox.sharedGreenWire(example));
                parity.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.systemWriter(example),
                    l1j.server.server.model.network.core.sc.packet.SC_SystemMessage.sharedWire(example));
                parity.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.scoreWriter(example),
                    l1j.server.server.model.network.core.sc.packet.SC_PacketBox.sharedScoreWire(example));
                parity.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.townLeaderWriter(example),
                    l1j.server.server.model.network.core.sc.packet.SC_PacketBox.sharedTownLeaderWire(example));
                parity.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.colorNoticeWriter(51,example),
                    l1j.server.server.model.network.core.sc.packet.SC_PacketBox.sharedColorNoticeWire(51,example));
                parity.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.serverMessageWriter(424,example),
                    l1j.server.server.model.network.core.sc.packet.SC_ServerMessage.sharedWire(424,example));
                parity.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.serverMessageWriter(424,example,null,"ABC"),
                    l1j.server.server.model.network.core.sc.packet.SC_ServerMessage.sharedWire(424,example,null,"ABC"));
                parity.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.chatWriter(16,5,987,example,true),
                    l1j.server.server.model.network.core.sc.packet.SC_ChatPacket.sharedWire(16,5,987,example,true));
            }
            for(int n:new int[]{0,1,123,65535,-1,Integer.MIN_VALUE}){
                parity.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.displayEffectWriter(n),
                    l1j.server.server.model.network.core.sc.packet.SC_DisplayEffect.sharedWire(n));
                parity.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.mapTimerWriter(n),
                    l1j.server.server.model.network.core.sc.packet.SC_PacketBox.sharedMapTimerWire(n));
                parity.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.roundWriter(n),
                    l1j.server.server.model.network.core.sc.packet.SC_PacketBox.sharedRoundWire(n));
                parity.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.miniGameTimeWriter(n),
                    l1j.server.server.model.network.core.sc.packet.SC_PacketBox.sharedMiniGameTimeWire(n));
                parity.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.hadinDisplayWriter(n),
                    l1j.server.server.model.network.core.sc.packet.SC_PacketBox.sharedHadinDisplayWire(n));
                parity.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.dragonRaidBuffWriter(n),
                    l1j.server.server.model.network.core.sc.packet.SC_PacketBox.sharedDragonRaidBuffWire(n));
                parity.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.emblemStatusWriter(n),
                    l1j.server.server.model.network.core.sc.packet.SC_PacketBox.sharedEmblemStatusWire(n));
                parity.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.lfcTrapWriter(n,n),
                    l1j.server.server.model.network.core.sc.packet.SC_LFCTrapEffect.sharedWire(n,n));
                parity.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.effectLocationWriter(n,n,n),
                    l1j.server.server.model.network.core.sc.packet.SC_EffectLocation.sharedWire(n,n,n));
            }
            parity.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.miniGameEndWriter(),
                l1j.server.server.model.network.core.sc.packet.SC_PacketBox.sharedMiniGameEndWire());
            parity.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.serverMessageWriter(810),
                l1j.server.server.model.network.core.sc.packet.SC_ServerMessage.sharedWire(810));
            System.out.println("PHASE82_NATIVE_WRITER_BYTE_PARITY_PASS");

            // Native shared Source owner: send one native plaintext to four
            // separate socket keys, verifying on-wire bytes after encryption.
            final String text82="Phase82-Korean 가나 / once";
            final byte[] expected82=l1j.server.server.model.network.core.sc.packet.SC_PacketBox.sharedGreenWire(text82);
            try(var shared82=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.greenWriter(text82),
                    Integer.MIN_VALUE,Integer.MAX_VALUE)) {
                for(int i=0;i<4;i++)
                    check(shared82.send(world.users[i],i+100),
                         "phase82 native multi-key send admission "+i);
                for(int i=0;i<4;i++)
                    world.read(i,java.util.Arrays.copyOfRange(expected82,2,expected82.length),false);
            }
            System.out.println("PHASE82_NATIVE_FOUR_KEY_SHARED_SOURCE_PASS");
"""
s=s.replace(marker,marker+"\n"+block,1)
p.write_text(s,encoding="utf-8")
assert s.count("PHASE82_NATIVE_FOUR_KEY_SHARED_SOURCE_PASS")==1
print("PHASE82_WINDOWS_EXTENDED_PARITY_PREPARED",flush=True)
