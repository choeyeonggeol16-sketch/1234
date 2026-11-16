#!/usr/bin/env python3
"""Phase83: remaining SC direct Native writers, Proto >16K, FIFO and 4-key Windows oracle."""
import sys,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
WORK=Path(sys.argv[1]).resolve()
subprocess.run([sys.executable,
    str(ROOT/"patches/nc_broadcast_phase82_20261116/prepare_windows_test.py"),
    str(WORK)],check=True)
p=WORK/"testsrc/l1j/server/server/model/network/core/connection/CSharedBroadcastWindowsTest.java"
s=p.read_text(encoding="utf-8")
marker="try(World world=new World()) {"
assert s.count(marker)==1
block=r"""
            // Phase83: direct Native payload parity with actual old SC packet
            // factories, including all trailer and MS949/NUL conventions.
            final java.util.function.BiConsumer<
                    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter,byte[]>
                parity83=(w,expected)->{
                    check(w!=null && expected!=null,"phase83 missing oracle/writer");
                    final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(16384)
                            .order(java.nio.ByteOrder.LITTLE_ENDIAN);
                    b.position(2);w.write(b);
                    int len=b.position();b.putShort(0,(short)len);
                    check(java.util.Arrays.equals(java.util.Arrays.copyOf(b.array(),len),expected),
                          "phase83 custom SC exact wire diff length="+len);
                };
            final var x83=l1j.server.server.model.network.core.broadcast.CSharedBroadcast.class;
            final var pkt83=l1j.server.server.model.network.core.sc.packet.SC_PacketBox.class;
            for(int id83:new int[]{0,1,0x11223344,-1}){
                parity83.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.moveObjectWriter(id83,1024,333,7),
                    l1j.server.server.model.network.core.sc.packet.SC_MoveChar.broadcastWire(id83,1024,333,7));
                parity83.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.actionWriter(id83,19),
                    l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX.broadcastWire(id83,19));
                parity83.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.removeObjectWriter(id83),
                    l1j.server.server.model.network.core.sc.packet.SC_RemoveObject.broadcastWire(id83));
                parity83.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.skillSoundWriter(id83,491),
                    l1j.server.server.model.network.core.sc.packet.SC_SkillSound.sharedWire(id83,491));
                parity83.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.castleMasterWriter(4,id83),
                    l1j.server.server.model.network.core.sc.packet.SC_CastleMaster.sharedWire(4,id83));
                parity83.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.partyColorWriter(id83,2),
                    l1j.server.server.model.network.core.sc.packet.SC_Party.sharedNameColorWire(id83,2));
                parity83.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.trueTargetWriter(id83,true),
                    l1j.server.server.model.network.core.sc.packet.SC_TrueTargetNew.sharedWire(id83,true));
                parity83.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.gameRapWriter(id83),
                    l1j.server.server.model.network.core.sc.packet.SC_GameRap.sharedWire(id83));
            }
            parity83.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.gameStartWriter(),
                l1j.server.server.model.network.core.sc.packet.SC_GameStart.sharedWire());
            parity83.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.gameEndWriter(),
                l1j.server.server.model.network.core.sc.packet.SC_GameEnd.sharedWire());
            parity83.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.gameOverWriter(),
                l1j.server.server.model.network.core.sc.packet.SC_GameOver.sharedWire());
            parity83.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.racingWriter(),
                l1j.server.server.model.network.core.sc.packet.SC_EtcPacket.sharedRacingWire());
            final byte[] bytes83=new byte[]{1,2,0,0,21,33,-1};
            parity83.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.actionUiFlagWriter(bytes83),
                l1j.server.server.model.network.core.sc.packet.SC_ActionUI.sharedFlagWire(bytes83));
            parity83.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.askWriter(954,"가나"),
                l1j.server.server.model.network.core.sc.packet.SC_MessageYN.sharedWire(954,"가나"));
            parity83.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.askIndexedWriter(993,954,"왕국"),
                l1j.server.server.model.network.core.sc.packet.SC_MessageYN.sharedIndexedWire(993,954,"왕국"));
            parity83.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.gmIconWriter("GM macro"),
                l1j.server.server.model.network.core.sc.packet.SC_IconMessage.sharedGmWire("GM macro"));
            parity83.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.macroWriter(17,"공지 가나","관리자"),
                l1j.server.server.model.network.core.sc.packet.SC_ChatMessageNoti.sharedMacroWire(17,"공지 가나","관리자"));
            parity83.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.macroWriter(1,"Hello",null),
                l1j.server.server.model.network.core.sc.packet.SC_ChatMessageNoti.sharedMacroWire(1,"Hello",null));
            parity83.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.targetChatWriter("인형",3,"테스트"),
                l1j.server.server.model.network.core.sc.packet.SC_ChatPacket.sharedTargetWire("인형",3,"테스트"));
            parity83.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.showPkWriter(
                    new byte[]{65,66},new byte[]{77,99}),
                l1j.server.server.model.network.core.sc.packet.SC_ShowCmd.sharedPkWire(
                    new byte[]{65,66},new byte[]{77,99}));
            parity83.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.clanMentWriter(true,"혈맹"),
                l1j.server.server.model.network.core.sc.packet.SC_ClanAttention.sharedMentWire(true,"혈맹"));
            final java.util.List<String> list83=new java.util.ArrayList<>();
            list83.add("Bob");list83.add(null);list83.add("가나다");list83.add("");
            final byte[] frozenList83=l1j.server.server.model.network.core.sc.packet.SC_ClanAttention.sharedListWire(4,list83);
            final var listWriter83=l1j.server.server.model.network.core.broadcast.CSharedBroadcast.clanListWriter(4,list83);
            list83.add("SHOULD_NOT_APPEAR");
            parity83.accept(listWriter83,frozenList83);
            System.out.println("PHASE83_SC_FACTORY_NATIVE_PARITY_PASS");

            // Proto raw: no framed byte[] intermediary; full 2-byte length
            // is generated by native source seal or direct CIO frame writer.
            final byte[] small83=new byte[100];
            for(int i=0;i<small83.length;i++)small83[i]=(byte)(i*13+7);
            final byte[] framed83=new byte[small83.length+2];
            framed83[0]=(byte)(small83.length+2);
            System.arraycopy(small83,0,framed83,2,small83.length);
            parity83.accept(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.rawWriter(small83),
                framed83);
            for(final int size83:new int[]{16385,32768,65535}){
                final byte[] raw83=new byte[size83-2];
                for(int i=0;i<raw83.length;i++)raw83[i]=(byte)(i*19+25);
                try(var direct83=l1j.server.server.model.network.core.broadcast.CSharedBroadcast.openWrittenRaw(raw83)){
                    check(direct83!=null,"phase83 raw source open "+size83);
                    check(direct83.send(world.users[0],Integer.MIN_VALUE+1),
                        "phase83 large Proto direct CIO "+size83);
                    world.read(0,raw83,false);
                }
                until(world.users[0]::isWriteIdle);
                System.out.println("PHASE83_LARGE_RAW_"+size83+"_FIFO_PASS");
            }
            System.out.println("PHASE83_RAW_PROTO_NATIVE_IOCP_PASS");
"""
s=s.replace(marker,marker+"\n"+block,1)
p.write_text(s,encoding="utf-8")
assert s.count("PHASE83_RAW_PROTO_NATIVE_IOCP_PASS")==1
print("PHASE83_WINDOWS_NATIVE_FACTORY_PARITY_PREPARED",flush=True)
