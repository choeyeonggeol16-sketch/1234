#!/usr/bin/env python3
"""Phase19: migrate identical party/raid/LFC event broadcasts to shared native
CTransport constant input. Explicit array helper avoids an Arrays.asList
allocation on in-dungeon execution paths. Per-receiver FIFO stays unchanged.
"""
import hashlib,json,zipfile,argparse
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/"downloads/RKern_Broadcast_NonSC_Phase18_FULL_SRC_20261115.zip"
OUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase19_FULL_SRC_20261115.zip"
PIN="5052f9bc337e907d73adaf8f85a0b38311bcfd6a"
BASE="src/l1j/server/"
SHARED=BASE+"server/model/network/core/broadcast/CSharedBroadcast.java"
TRAP=BASE+"MJInstanceSystem/MJLFC/Template/MJLFCWarSquareTrap.java"
HADIN=BASE+"IndunSystem/Hadin/Hadin.java"
ORIM=BASE+"IndunSystem/Orim/OrimController.java"
RAID=BASE+"MJRaidSystem/MJRaidObject.java"
TARGETS=[SHARED,TRAP,HADIN,ORIM,RAID]
CLS="l1j.server.server.model.network.core.broadcast.CSharedBroadcast"

def blobsha(buf):return hashlib.sha1(b"blob "+str(len(buf)).encode()+b"\0"+buf).hexdigest()
def method(src,sig):
    assert src.count(sig)==1,(sig,src.count(sig))
    a=src.index(sig); b=src.index("{",a);n=0
    for i in range(b,len(src)):
        if src[i]=="{":n+=1
        elif src[i]=="}":
            n-=1
            if n==0:return a,i+1,src[a:i+1]
    raise AssertionError(sig)
def patch(files):
    def edit(name,fn): files[name]=fn(files[name].decode('utf8')).encode('utf8')
    def shared(src):
        marker="    private static void sendGroup("
        assert src.count(marker)==1
        add=r'''    /** Array event recipients, no temporary iterable or per-recipient encoder.
     * Original order and duplicate members are maintained.
     */
    public static void sendArray(L1PcInstance[] members,byte[] wire) {
        if(members==null || wire==null)return;
        try(var shared=new CAsyncSendConstBuffer(wire,Integer.MIN_VALUE,Integer.MAX_VALUE)){
            for(L1PcInstance pc:members) {
                if(pc==null)continue;
                final CIOClient client=pc.getNetConnection();
                if(client==null || !client.isConnected())continue;
                final var socket=client.getConnection();
                if(socket!=null && socket.isClientConnected())
                    shared.send(socket,pc.getId());
            }
        }
    }

'''
        return src.replace(marker,add+marker,1)
    edit(SHARED,shared)
    def trap(src):
        sig="private void broadcastEffect(int effect) {"
        a,b,old=method(src,sig)
        for needle in ("for (L1PcInstance pc : _red) if (pc != null) SC_DisplayEffect.send(pc, effect);",
                       "for (L1PcInstance pc : _blue) if (pc != null) SC_DisplayEffect.send(pc, effect);"):
            assert needle in old,needle
        replacement="""private void broadcastEffect(int effect) {
        """+CLS+""".sendGroups(_red,_blue,SC_DisplayEffect.sharedWire(effect));
    }"""
        return src[:a]+replacement+src[b:]
    edit(TRAP,trap)
    def hadin(src):
        sig="private void BonginSendPacekt(int count){"
        a,b,old=method(src,sig)
        assert 'getParty().getMembers()' in old
        assert 'SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "$"+count);' in old
        return src[:a]+"""private void BonginSendPacekt(int count){
        """+CLS+""".sendArray(
                getParty().getMembers(), SC_PacketBox.sharedGreenWire("$"+count));
    }"""+src[b:]
    edit(HADIN,hadin)
    def orim(src):
        sig="private void sendMessage(String msg) {"
        a,b,old=method(src,sig)
        assert "getPlayMemberArray()" in old and "SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, msg);" in old
        return src[:a]+"""private void sendMessage(String msg) {
        """+CLS+""".sendArray(
                getPlayMemberArray(), SC_PacketBox.sharedGreenWire(msg));
    }"""+src[b:]
    edit(ORIM,orim)
    def raid(src):
        needle="for(L1PcInstance p : getUsersView()){"
        a,b,old=method(src,needle)
        assert "SC_SystemMessage.send(p, msg);" in old
        assert "if(p != null)" in old
        return src[:a]+"""SC_SystemMessage.broadcastRecipients(getUsersView(),msg);"""+src[b:]
    edit(RAID,raid)

PARITY=r'''import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Arrays;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.*;
public final class Phase19WireParity {
  static int count;
  static void check(boolean ok){if(!ok)throw new AssertionError("phase19 byte parity "+count);count++;}
  static byte[] effect(int v){
    return ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
      .putShort((short)10).put((byte)Opcodes.S_EVENT).put((byte)0x53)
      .putInt(v).putShort((short)0).array();
  }
  static byte[] notice(String s){
    byte[] text=(s==null?"":s).getBytes(Charset.forName("MS949"));
    byte[] a=new byte[6+text.length];
    a[0]=(byte)a.length;a[1]=(byte)(a.length>>>8);
    a[2]=(byte)Opcodes.S_EVENT;a[3]=(byte)SC_PacketBox.GREEN_MESSAGE;a[4]=2;
    System.arraycopy(text,0,a,5,text.length);
    return a;
  }
  public static void main(String[] args){
    for(int v:new int[]{0,-1,Integer.MIN_VALUE,Integer.MAX_VALUE,0x12345678,4,3})
       check(Arrays.equals(effect(v),SC_DisplayEffect.sharedWire(v)));
    for(String s:new String[]{"","$1","$100","Korean 한글",null,"시간 종료"})
       check(Arrays.equals(notice(s),SC_PacketBox.sharedGreenWire(s)));
    int n=0x67a68cf;
    for(int i=0;i<4096;i++){
       n^=n<<13;n^=n>>>17;n^=n<<5;
       check(Arrays.equals(effect(n),SC_DisplayEffect.sharedWire(n)));
       check(Arrays.equals(notice("$"+n),SC_PacketBox.sharedGreenWire("$"+n)));
    }
    System.out.println("PHASE19_WIRE_PARITY_OK "+count);
  }
}'''
def main():
    arg=argparse.ArgumentParser();arg.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase19"));args=arg.parse_args()
    raw=SRC.read_bytes()
    assert blobsha(raw)==PIN,"phase18 baseline mismatch"
    with zipfile.ZipFile(SRC) as z:
        assert z.testzip() is None
        files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
    orig=files.copy()
    assert sum(p.endswith('.java') for p in files)==1710
    assert "CTransport.dll" in files
    patch(files)
    changed=sorted(p for p in files if files[p]!=orig[p])
    assert changed==sorted(TARGETS),changed
    assert files["CTransport.dll"]==orig["CTransport.dll"]
    args.work.mkdir(parents=True,exist_ok=True)
    for p,data in files.items():
        if p.startswith("src/") and p.endswith(".java"):
            f=args.work/p;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(data)
    f=args.work/"testsrc/Phase19WireParity.java";f.parent.mkdir(parents=True,exist_ok=True);f.write_text(PARITY)
    OUT.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for p,data in sorted(files.items()):z.writestr(p,data)
    with zipfile.ZipFile(OUT) as z:
        assert z.testzip() is None and set(z.namelist())==set(files)
    info={"phase":19,"input_blob":PIN,"output_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
        "modified_java":changed,"java_sources":1710,
        "five_source_changes":True,"native_dll_unchanged":True,
        "shared_party_green":True,"shared_raid_notice":True,"shared_LFC_effect":True,
        "shared_orim_notice":True,"no_new_threads_queues_classes":True,
        "runtime_windows_tested":False}
    (args.work/"audit.json").write_text(json.dumps(info,indent=2)+"\n")
    print(json.dumps(info,indent=2))
if __name__=="__main__":main()
