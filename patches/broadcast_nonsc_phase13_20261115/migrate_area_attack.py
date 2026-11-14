#!/usr/bin/env python3
"""Phase13: shared immutable SC_AreaAttackEffect packet across self + AOI.
Retain original recipient order/duplicates, target list snapshot, seq once
per cast, and 2-byte little endian attack-many packet framing.
"""
from pathlib import Path
import hashlib,zipfile,json,argparse
ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/"downloads/RKern_Broadcast_NonSC_Phase12_FULL_SRC_20261115.zip"
OUTPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase13_FULL_SRC_20261115.zip"
PIN="f97f2f34eaaa469d55e5b52c9e802badc6a6ef42"
TARGET="src/l1j/server/server/model/network/core/sc/packet/SC_AreaAttackEffect.java"

def blobsha(buf):return hashlib.sha1(b"blob "+str(len(buf)).encode()+b"\0"+buf).hexdigest()
def bounds(s,sig):
    assert s.count(sig)==1,(sig,s.count(sig))
    a=s.index(sig);b=s.index("{",a);depth=0
    for i in range(b,len(s)):
        if s[i]=="{":depth+=1
        elif s[i]=="}":
            depth-=1
            if depth==0:return a,i+1,s[a:i+1]
    raise AssertionError("invalid method braces")

NEW=r'''public static void sendNoDirAndBroadcast(L1Character caster, ArrayList<L1Character> targets,
                                              int gfx, int action) {
        if(caster==null)return;
        final int id=caster.getId(),x=caster.getX(),y=caster.getY();
        final int heading=caster.getHeading();
        final int count=targets==null?0:targets.size();
        final int[] ids=new int[count];
        for(int i=0;i<count;i++) {
            final L1Character target=targets.get(i);
            if(target!=null)ids[i]=target.getId();
        }
        final int seq=SEQUENCE.incrementAndGet();
        final byte[] wire=sharedWire(id,x,y,heading,seq,gfx,action,ids);
        if(wire==null)return;
        try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                wire,Integer.MIN_VALUE,Integer.MAX_VALUE)) {
            // Preserve the old self-first emission even if the AOI query also
            // contains that player; duplicate recipients stay duplicated.
            if(caster instanceof L1PcInstance)
                sendShared(shared,(L1PcInstance)caster);
            for(L1PcInstance viewer:L1World.getInstance().getVisiblePlayer(caster))
                sendShared(shared,viewer);
        }
    }

    /** Original SC_AreaAttackEffect::send WriteBuffer byte layout. One buffer
     * per attack; per-socket encryption and SEND FIFO remain native-owned.
     */
    public static byte[] sharedWire(int id,int x,int y,int heading,int seq,int gfx,
                                    int action,int[] ids) {
        if(ids==null)return null;
        final long length=26L+6L*ids.length;
        if(length>0xffff)return null;
        final int size=(int)length;
        final byte[] wire=new byte[size];
        put16(wire,0,size);
        wire[2]=(byte)Opcodes.S_ATTACK_MANY;
        wire[3]=(byte)action;
        put32(wire,4,id);
        put16(wire,8,x);
        put16(wire,10,y);
        wire[12]=(byte)heading;
        put32(wire,13,seq);
        put16(wire,17,gfx);
        // 19: byte 0; 20-21: short 0
        put16(wire,22,ids.length);
        int at=24;
        for(int targetId:ids) {
            put32(wire,at,targetId);at+=4;
            wire[at++]=0x20;
            wire[at++]=(byte)ActionCodes.ACTION_Damage;
        }
        // at..at+1: terminal short 0
        return wire;
    }

    private static void put16(byte[] wire,int at,int value) {
        wire[at]=(byte)value;
        wire[at+1]=(byte)(value>>>8);
    }
    private static void put32(byte[] wire,int at,int value) {
        wire[at]=(byte)value;
        wire[at+1]=(byte)(value>>>8);
        wire[at+2]=(byte)(value>>>16);
        wire[at+3]=(byte)(value>>>24);
    }
    private static void sendShared(
            l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer shared,
            L1PcInstance pc) {
        if(pc==null)return;
        final CIOClient client=pc.getNetConnection();
        if(client==null || !client.isConnected())return;
        final CUserSocket socket=client.getConnection();
        if(socket!=null && socket.isClientConnected())
            shared.send(socket,pc.getId());
    }'''

# Native immutable CTransport input is strictly limited to a 16 KiB frame;
# preserve legacy 65,535-byte area frames with the existing Java FIFO direct
# lease, but serialize the payload only once, not once per recipient.
NEW=NEW.replace('        try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(', '''        if(wire.length>16384) {
            final byte[] payload=java.util.Arrays.copyOfRange(wire,2,wire.length);
            if(caster instanceof L1PcInstance)
                sendLarge((L1PcInstance)caster,wire.length,payload);
            for(L1PcInstance viewer:L1World.getInstance().getVisiblePlayer(caster))
                sendLarge(viewer,wire.length,payload);
            return;
        }
        try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(''',1)
NEW=NEW.replace('    private static void sendShared(', '''    /** Full 65,535-byte game frames use the existing large direct FIFO lease.
     * The encoded body is shared across all recipients; only the mandatory
     * per-socket buffer copy is performed, and the original encoder is gone.
     */
    private static boolean sendLarge(L1PcInstance pc,int length,byte[] payload) {
        if(pc==null)return false;
        final CIOClient client=pc.getNetConnection();
        if(client==null || !client.isConnected())return false;
        final CUserSocket connection=client.getConnection();
        if(connection==null || !connection.isClientConnected())return false;
        final CIOFrame frame=connection.beginDirectFrame(length);
        if(frame==null)return false;
        boolean committed=false;
        try {
            frame.buffer().writeBytes(payload);
            committed=connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if(!committed)connection.abortDirectFrame(frame);
        }
    }

    private static void sendShared(''',1)

def patch(s):
    a,b,old=bounds(s,"public static void sendNoDirAndBroadcast(L1Character caster, ArrayList<L1Character> targets,")
    for word in ("SEQUENCE.incrementAndGet()","send((L1PcInstance) caster","getVisiblePlayer(caster)","send(pc, id, x, y, heading, seq, gfx, action, ids)"):
        assert word in old,word
    s=s[:a]+NEW+s[b:]
    sig="private static boolean send(L1PcInstance pc, int id, int x, int y, int heading,"
    a,b,old=bounds(s,sig)
    for word in ("Opcodes.S_ATTACK_MANY","b.writeH(ids.length)","ActionCodes.ACTION_Damage","connection.commitDirectFrame(frame)"):
        assert word in old,word
    s=s[:a]+s[b:]
    assert "sendNoDirAndBroadcast" in s and "private static boolean send(" not in s
    return s

TEST=r'''import java.util.Arrays;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import l1j.server.server.ActionCodes;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.SC_AreaAttackEffect;
public final class SharedAreaAttackParity13 {
 static int checked;
 static byte[] original(int id,int x,int y,int heading,int seq,int gfx,int action,int[] ids) {
    int size=26+6*ids.length;
    ByteBuffer w=ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    w.putShort((short)size);
    w.put((byte)Opcodes.S_ATTACK_MANY).put((byte)action).putInt(id);
    w.putShort((short)x).putShort((short)y).put((byte)heading);
    w.putInt(seq).putShort((short)gfx).put((byte)0).putShort((short)0).putShort((short)ids.length);
    for(int target:ids)w.putInt(target).put((byte)0x20).put((byte)ActionCodes.ACTION_Damage);
    w.putShort((short)0);
    if(w.position()!=size)throw new AssertionError("reference wire len");
    return w.array();
 }
 static void check(int id,int x,int y,int heading,int seq,int gfx,int action,int[] targets) {
    byte[] a=original(id,x,y,heading,seq,gfx,action,targets);
    byte[] b=SC_AreaAttackEffect.sharedWire(id,x,y,heading,seq,gfx,action,targets);
    if(!Arrays.equals(a,b))throw new AssertionError("wire "+checked+" "+a.length);
    checked++;
 }
 public static void main(String[] ignored) {
    check(0,0,0,0,0,0,0,new int[0]);
    check(-1,65535,-32768,8,Integer.MAX_VALUE,123456,255,new int[]{0,-1,Integer.MIN_VALUE});
    check(0x12345678,32768,512,255,-1,888,18,new int[10000]);
    if(SC_AreaAttackEffect.sharedWire(0,0,0,0,0,0,0,new int[10919])!=null)
       throw new AssertionError("oversized frame accepted");
    int r=0x59bfe1c;
    for(int i=0;i<4096;i++) {
       r^=r<<13;r^=r>>>17;r^=r<<5;
       int[] ids=new int[i%31];
       for(int j=0;j<ids.length;j++)ids[j]=r^((j+1)*0x9e3779b9);
       check(r,r>>>7,r<<3,r>>>13,r*31,r>>>11,i,ids);
    }
    System.out.println("AREA_ATTACK_PARITY13_OK "+checked);
 }
}'''
def main():
    p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase13"))
    args=p.parse_args();raw=SOURCE.read_bytes()
    assert blobsha(raw)==PIN,"Phase12 ZIP mismatch"
    with zipfile.ZipFile(SOURCE) as z:
        assert z.testzip() is None
        files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
    old=files.copy()
    assert sum(n.endswith(".java") for n in files)==1711 and "CTransport.dll" in files
    files[TARGET]=patch(files[TARGET].decode("utf8")).encode("utf8")
    modified=sorted(n for n in files if files[n]!=old[n])
    assert modified==[TARGET],modified
    assert files["CTransport.dll"]==old["CTransport.dll"]
    args.work.mkdir(parents=True,exist_ok=True)
    for n,v in files.items():
        if n.startswith("src/") and n.endswith(".java"):
            d=args.work/n;d.parent.mkdir(parents=True,exist_ok=True);d.write_bytes(v)
    d=args.work/"testsrc/SharedAreaAttackParity13.java";d.parent.mkdir(parents=True,exist_ok=True);d.write_text(TEST)
    OUTPUT.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(OUTPUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for n,v in sorted(files.items()):z.writestr(n,v)
    with zipfile.ZipFile(OUTPUT) as z:
        assert z.testzip() is None and set(z.namelist())==set(files)
    report={"phase":13,"input_blob":PIN,"output_sha256":hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),
            "modified_java":modified,"java_sources":1711,"shared_area_attack":True,
            "self_first_and_duplicates_preserved":True,"target_array_and_seq_preserved":True,
            "native_unchanged":True,"no_fallback":True,"large_frame_direct_FIFO":True,"windows_runtime_tested":False}
    (args.work/"audit.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
    print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=="__main__":main()
