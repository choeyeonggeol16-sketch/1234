#!/usr/bin/env python3
"""73: extend existing real Windows IOCP 4-cipher test with six exact L1 packet layouts.

Baseline 72 Windows fixture is reused unchanged; only six additional
native pooled Assemble calls are exercised. Tests send Native Source to 4
distinct encrypted IOCP sockets and verify all original L1 wire fields.
"""
from pathlib import Path
import subprocess,sys
ROOT=Path(__file__).resolve().parents[2]
work=Path(sys.argv[1]).resolve()
subprocess.run([sys.executable,
        str(ROOT/"patches/nc_pooled_phase69_20261116/prepare_windows_test.py"),
        str(work)],check=True)
p=work/"testsrc/l1j/server/server/model/network/core/connection/CSharedBroadcastWindowsTest.java"
s=p.read_text(encoding="utf-8")
marker="try(World world=new World()) {"
assert s.count(marker)==1,s.count(marker)
test=r'''
            // PHASE73 real Windows IOCP: the same native refcounted source
            // goes to 4 different per-connection rolling cipher contexts.
            final java.lang.reflect.Method formatted =
                    l1j.server.server.model.network.core.broadcast.CAsyncSendConstBufferExceptSelf.class
                            .getDeclaredMethod("forFormatted", CUserSocket.class,
                                    int.class,int.class,int.class,int.class,
                                    int.class,int.class,int.class);
            formatted.setAccessible(true);
            final int objectId = 0x81234567;
            final int[][] cases = new int[][] {
                    {3,l1j.server.server.Opcodes.S_CHANGE_DIRECTION,objectId,7,0,0},
                    {4,l1j.server.server.Opcodes.S_SOUND_EFFECT,0x1234,0,0,0},
                    {5,l1j.server.server.Opcodes.S_EFFECT,objectId,0x1234,0,0},
                    {6,l1j.server.server.Opcodes.S_REMOVE_OBJECT,objectId,0,0,0},
                    {7,l1j.server.server.Opcodes.S_EFFECT,objectId,0x1234,0,0},
                    {8,l1j.server.server.Opcodes.S_EFFECT_LOC,0x1234,0x5678,0x4321,0}
            };
            for (int caseIndex=0;caseIndex<cases.length;caseIndex++) {
                final int[] v = cases[caseIndex];
                final java.nio.ByteBuffer b = java.nio.ByteBuffer
                        .allocate(switch(v[0]){
                            case 3 -> 8; case 4 -> 6; case 5,6 -> 9;
                            case 7 -> 15; case 8 -> 10;
                            default -> throw new AssertionError("bad phase73 kind");
                        }).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                final int n=b.capacity();
                b.putShort((short)n).put((byte)v[1]);
                switch(v[0]) {
                    case 3 -> b.putInt(v[2]).put((byte)v[3]);
                    case 4 -> b.put((byte)0).putShort((short)v[2]);
                    case 5 -> b.putInt(v[2]).putShort((short)v[3]);
                    case 6 -> b.putInt(v[2]).putShort((short)0);
                    case 7 -> b.putInt(v[2]).putShort((short)v[3])
                                    .putShort((short)0).putInt(0);
                    case 8 -> b.putShort((short)v[2]).putShort((short)v[3])
                                    .putShort((short)v[4]).put((byte)0);
                    default -> throw new AssertionError("unknown phase73 kind");
                }
                check(b.position()==n,"phase73 original codec length mismatch "+caseIndex);
                final byte[] expected = b.array();
                try (var pooled = (CAsyncSendConstBufferExceptSelf)
                        formatted.invoke(null,world.users[0],999,
                                v[0],v[1],v[2],v[3],v[4],v[5])) {
                    check(pooled!=null,"phase73 Native pooled Source unavailable "+caseIndex);
                    for (int k=0;k<4;k++) {
                        check(pooled.send(world.users[k],100+k),
                                "phase73 four cipher SEND failed "+caseIndex+":"+k);
                    }
                }
                for(int k=0;k<4;k++)
                    world.read(k,Arrays.copyOfRange(expected,2,n),false);
                for(CUserSocket user:world.users)until(user::isWriteIdle);
                System.out.println("PHASE73_NC_ASSEMBLE_KIND_"+v[0]+"_4_KEYS_PASS");
            }
            System.out.println("PHASE73_NC_ASSEMBLE_6_PACKET_24_REAL_IOCP_SEND_PASS");
'''
s=s.replace(marker,marker+"\n"+test,1)
p.write_text(s,encoding="utf-8")
assert s.count("PHASE73_NC_ASSEMBLE_6_PACKET_24_REAL_IOCP_SEND_PASS")==1
print("PHASE73_WINDOWS_6_PACKET_FIXTURE_PREPARED",len(list((work/"src").rglob("*.java"))))
