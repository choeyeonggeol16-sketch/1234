#!/usr/bin/env python3
"""Phase81: repeat all 73..80 real IOCP oracles and cover long MS949/S2 boundary."""
from pathlib import Path
import subprocess,sys
ROOT=Path(__file__).resolve().parents[2]
WORK=Path(sys.argv[1]).resolve()
subprocess.run([sys.executable,str(ROOT/"patches/nc_assemble_broadcast_phase80_20261116/prepare_windows_test.py"),str(WORK)],check=True)
p=WORK/"testsrc/l1j/server/server/model/network/core/connection/CSharedBroadcastWindowsTest.java"
s=p.read_text(encoding="utf-8")
anchor="try(World world=new World()) {"
assert s.count(anchor)==1
test=r"""
            // Phase81 exactly preserves the old writeS2 refusal policy.
            // Old SC_IconMessage.sharedWire() returned null before fanout for
            // encoded MS949 byte count >255; the Native path must not throw.
            final java.lang.reflect.Method s2Predicate=
                l1j.server.server.model.network.core.sc.packet.SC_IconMessage.class
                .getDeclaredMethod("textFitsS2",String.class);
            s2Predicate.setAccessible(true);
            final java.nio.charset.Charset s2Charset=java.nio.charset.Charset.forName("MS949");
            final String[] boundary={
                "", "a", "A".repeat(127), "A".repeat(255), "A".repeat(256),
                "한".repeat(127), "한".repeat(128), "한".repeat(255),
                "a".repeat(250)+"한", "한글".repeat(63), "한글".repeat(64),
                "\uD83D\uDE00".repeat(128),
                "A".repeat(16384), "\uD83D\uDE00".repeat(500),
                "한".repeat(33000)
            };
            for(String candidate:boundary) {
                boolean expected=candidate.getBytes(s2Charset).length<=255;
                boolean accepted=(boolean)s2Predicate.invoke(null,candidate);
                check(accepted==expected,"phase81 S2 wrong encoded-size gating chars="+candidate.length());
                if(accepted) {
                    java.nio.ByteBuffer target=java.nio.ByteBuffer.allocate(512)
                        .order(java.nio.ByteOrder.LITTLE_ENDIAN);
                    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.writeMS949S2(
                        target,candidate);
                    check((target.get(0)&255)==candidate.getBytes(s2Charset).length,
                        "phase81 Native S2 byte length mismatch");
                }
            }
            final java.util.Random fuzz=new java.util.Random(817301);
            final char[] alphabet={'A','B','한','글','가','훠','\u00A9','\u2603',
                                   '\uD83D','\uDE00','\u0000','\u007F'};
            for(int n=0;n<1000;n++){
                final int size=fuzz.nextInt(300);
                StringBuilder input=new StringBuilder(size);
                for(int i=0;i<size;i++)input.append(alphabet[fuzz.nextInt(alphabet.length)]);
                String candidate=input.toString();
                boolean expected=candidate.getBytes(s2Charset).length<=255;
                check((boolean)s2Predicate.invoke(null,candidate)==expected,
                    "phase81 MS949 randomized overflow refusal mismatch n="+n);
            }
            System.out.println("PHASE81_S2_ALL_15_BOUNDARIES_AND_1000_RANDOM_CASES_PASS");
"""
s=s.replace(anchor,anchor+"\n"+test,1)
p.write_text(s,encoding="utf-8")
assert s.count("PHASE81_S2_ALL_15_BOUNDARIES_AND_1000_RANDOM_CASES_PASS")==1
print("PHASE81_MS949_S2_BOUNDARY_FIXTURE_PREPARED")
