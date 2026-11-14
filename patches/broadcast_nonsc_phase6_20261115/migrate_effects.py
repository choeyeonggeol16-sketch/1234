#!/usr/bin/env python3
"""Phase6: immutable multi-effect broadcasts, retaining recipient-major packet order.

Starts from exact phase5 whole-source ZIP. Only three Java sources change.
No ServerPacket fallback, packet rewrite, new threads/queues, or native DLL edits.
"""
import argparse
import hashlib
import json
import pathlib
import re
import zipfile

ROOT=pathlib.Path(__file__).resolve().parents[2]
SOURCE=ROOT/"downloads/RKern_Broadcast_NonSC_Phase5_FULL_SRC_20261115.zip"
OUTPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase6_FULL_SRC_20261115.zip"
GIT_BLOB="8bcd787c6b01a19575db42f83c733b47bee573a3"
AREA="src/l1j/server/MJCombatSystem/Games/MJBattleTournamentArea.java"
EFFECT="src/l1j/server/server/model/network/core/sc/packet/SC_DisplayEffect.java"
BCAST="src/l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java"

def blob_sha(data):
    return hashlib.sha1(b"blob "+str(len(data)).encode()+b"\0"+data).hexdigest()

def patch_effect(s):
    marker="    public static boolean send(L1PcInstance pc, int value) {"
    assert s.count(marker)==1
    assert "buffer.writeC(Opcodes.S_EVENT);" in s
    assert "buffer.writeC(0x53);" in s
    assert "buffer.writeD(value);" in s
    assert "buffer.writeH(0);" in s
    assert "public static byte[] sharedWire(" not in s
    helper='''    /** Exact S_EVENT, 0x53, int32(value), uint16(0) frame, including LE header. */
    public static byte[] sharedWire(int value) {
        final byte[] wire=new byte[10];
        wire[0]=10;
        wire[2]=(byte)Opcodes.S_EVENT;
        wire[3]=0x53;
        wire[4]=(byte)value;
        wire[5]=(byte)(value>>>8);
        wire[6]=(byte)(value>>>16);
        wire[7]=(byte)(value>>>24);
        return wire;
    }

'''
    return s.replace(marker,helper+marker,1)

def patch_shared(s):
    marker="    /** Global broadcast includes the sender and uses the normal socket FIFO. */"
    assert s.count(marker)==1
    assert "public static void sendMapTeam(" in s
    assert "public static void sendMapTeamFrames(" not in s
    helper='''    /**
     * Send identical frames in recipient-major order. Previous behavior was
     * for each visible PC: send all display effects in input array order.
     * Team filtering, map iteration order, and socket FIFO are unchanged.
     * This method never serializes inside the recipient iteration.
     */
    public static void sendMapTeamFrames(
            int mapId, int teamId, boolean teamOnly, byte[][] wires) {
        if (wires==null || wires.length==0) return;
        final CAsyncSendConstBuffer[] shared=
                new CAsyncSendConstBuffer[wires.length];
        int opened=0;
        try {
            for(int i=0;i<wires.length;i++) {
                if(wires[i]==null) return;
                shared[i]=new CAsyncSendConstBuffer(
                        wires[i],Integer.MIN_VALUE,Integer.MAX_VALUE);
                opened++;
            }
            for(L1PcInstance pc:L1World.getInstance().getVisiblePlayers(mapId)) {
                if(pc==null || (teamOnly && pc.get_current_combat_team_id()!=teamId))
                    continue;
                final CIOClient client=pc.getNetConnection();
                if(client==null || !client.isConnected()) continue;
                final var socket=client.getConnection();
                if(socket==null || !socket.isClientConnected()) continue;
                for(int i=0;i<opened;i++)
                    shared[i].send(socket,pc.getId());
            }
        } finally {
            for(int i=0;i<opened;i++) shared[i].close();
        }
    }

'''
    return s.replace(marker,helper+marker,1)

def bounds(s,signature):
    assert s.count(signature)==1,(signature,s.count(signature))
    a=s.index(signature)
    b=s.index("{",a)
    n=0
    for k in range(b,len(s)):
        if s[k]=="{":n+=1
        elif s[k]=="}":
            n-=1
            if n==0:return a,k+1,s[a:k+1]
    raise AssertionError("Unterminated Java method "+signature)

def patch_area(s):
    a,b,old=bounds(s,"private void broadcast(int[] packets, boolean ignored) {")
    assert old.count("SC_DisplayEffect.send(pc, effect)") == 1
    assert "L1World.getInstance().getVisiblePlayers(_map_id)" in old
    assert "_round_id < 4 && pc.get_current_combat_team_id() != _team_id" in old
    new='''private void broadcast(int[] packets, boolean ignored) {
        if (packets == null || packets.length == 0) return;
        final byte[][] frames=new byte[packets.length][];
        for(int i=0;i<packets.length;i++)
            frames[i]=SC_DisplayEffect.sharedWire(packets[i]);
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast
                .sendMapTeamFrames(_map_id,_team_id,_round_id<4,frames);
    }'''
    return s[:a]+new+s[b:]

TEST=r'''import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.SC_DisplayEffect;
import java.util.Arrays;
public final class DisplayEffectSharedParity {
    static int checks=0;
    static void ok(boolean v, String msg){if(!v)throw new AssertionError(msg);checks++;}
    static byte[] original(int value) {
        byte[] b=new byte[10];
        b[0]=10;
        b[1]=0;
        b[2]=(byte)Opcodes.S_EVENT;
        b[3]=0x53;
        b[4]=(byte)value;
        b[5]=(byte)(value>>8);
        b[6]=(byte)(value>>16);
        b[7]=(byte)(value>>24);
        b[8]=b[9]=0;
        return b;
    }
    public static void main(String[] ignored) {
        int v=0x12345678;
        for(int i=0;i<4096;i++) {
            v^=v<<13;v^=v>>>17;v^=v<<5;
            int number=i<16 ? new int[]{0,1,2,3,4,7,10,127,128,255,256,65535,
              -1,Integer.MIN_VALUE,Integer.MAX_VALUE,0x11223344}[i] : v;
            byte[] shared=SC_DisplayEffect.sharedWire(number);
            byte[] expected=original(number);
            ok(Arrays.equals(shared,expected),"wire "+i);
            ok(shared.length==10 && (shared[0]&255)==10 &&
                shared[8]==0 && shared[9]==0,"trailer "+i);
        }
        System.out.println("DISPLAY_EFFECT_SHARED_WIRE_PARITY_OK "+checks);
    }
}'''

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--input",type=pathlib.Path,default=SOURCE)
    p.add_argument("--output",type=pathlib.Path,default=OUTPUT)
    p.add_argument("--work",type=pathlib.Path,default=pathlib.Path("/tmp/broadcast-phase6"))
    args=p.parse_args()
    source=args.input.read_bytes()
    assert blob_sha(source)==GIT_BLOB,"Phase5 whole-source input mismatch"
    with zipfile.ZipFile(args.input) as z:
        assert z.testzip() is None
        files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
    old=dict(files)
    assert "CTransport.dll" in files
    assert sum(x.endswith(".java") for x in files)==1711
    for name,edit in [(EFFECT,patch_effect),(BCAST,patch_shared),(AREA,patch_area)]:
        before=files[name].decode("utf-8")
        after=edit(before)
        assert before!=after,name
        files[name]=after.encode("utf-8")
    changed=sorted(x for x in files if old[x]!=files[x])
    assert changed==sorted([EFFECT,BCAST,AREA]),changed
    assert files["CTransport.dll"]==old["CTransport.dll"]
    args.work.mkdir(parents=True,exist_ok=True)
    for name,data in files.items():
        if name.startswith("src/") and name.endswith(".java"):
            target=args.work/name
            target.parent.mkdir(parents=True,exist_ok=True)
            target.write_bytes(data)
    test=args.work/"testsrc/DisplayEffectSharedParity.java"
    test.parent.mkdir(parents=True,exist_ok=True)
    test.write_text(TEST)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(args.output,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for name,data in sorted(files.items()):
            z.writestr(name,data)
    with zipfile.ZipFile(args.output) as z:
        assert z.testzip() is None
        assert set(z.namelist())==set(files)
    report={
      "phase":6,"phase5_blob_sha":GIT_BLOB,
      "output_sha256":hashlib.sha256(args.output.read_bytes()).hexdigest(),
      "modified_files":changed,"full_java_sources":1711,
      "effect_wire_bytes":10,"encoded_once_per_effect":True,
      "recipient_major_order_preserved":True,"team_filter_preserved":True,
      "map_membership_preserved":True,"no_fallback":True,
      "native_dll_unchanged":True,"windows_runtime_tested":False
    }
    (args.work/"audit.json").write_text(json.dumps(report,indent=2,ensure_ascii=False)+"\n")
    print(json.dumps(report,indent=2,ensure_ascii=False))
if __name__=="__main__":main()
