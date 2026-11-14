#!/usr/bin/env python3
"""Phase12: replace LFC enum list notice fanouts with one shared frame per event.
Preserve list order, null entries, duplicated members, appended message and opcode.
"""
from pathlib import Path
import hashlib,zipfile,json,argparse
ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/"downloads/RKern_Broadcast_NonSC_Phase11_FULL_SRC_20261115.zip"
OUTPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase12_FULL_SRC_20261115.zip"
PIN="3095ab082568d79299ecb0270cc04491b562e8ae"
TARGET="src/l1j/server/MJInstanceSystem/MJInstanceEnums.java"
SHARED="l1j.server.server.model.network.core.broadcast.CSharedBroadcast"

def blobsha(data):
    return hashlib.sha1(b"blob "+str(len(data)).encode()+b"\0"+data).hexdigest()
def method(src,sig):
    assert src.count(sig)==1,(sig,src.count(sig))
    a=src.index(sig);b=src.index("{",a);depth=0
    for i in range(b,len(src)):
        if src[i]=="{":depth+=1
        elif src[i]=="}":
            depth-=1
            if depth==0:return a,i+1,src[a:i+1]
    raise AssertionError("unterminated "+sig)
def swap(src,sig,expected,replacement):
    a,b,old=method(src,sig)
    for e in expected:assert e in old,(sig,e)
    return src[:a]+replacement+src[b:]
def patch(src):
    assert "public enum LFCMessages" in src
    assert "public enum InstSpcMessages" in src
    assert "SC_SystemMessage" in src and "SC_PacketBox" in src
    # Only exact overloads in LFCMessages; InstSpcMessages has no list overload.
    src=swap(src,"public void sendSystemMsgToList(ArrayList<L1PcInstance> pcs){",
        ["for (L1PcInstance pc : pcs) SC_SystemMessage.send(pc, get());"],
        """public void sendSystemMsgToList(ArrayList<L1PcInstance> pcs){
            java.util.Objects.requireNonNull(pcs);
            SC_SystemMessage.broadcastRecipients(pcs,get());
        }""")
    src=swap(src,"public void sendSystemMsgToList(ArrayList<L1PcInstance> pcs, String msg){",
        ['new StringBuilder(get()).append(" ").append(msg).toString()',
         'for (L1PcInstance pc : pcs) SC_SystemMessage.send(pc, message);'],
        """public void sendSystemMsgToList(ArrayList<L1PcInstance> pcs, String msg){
            java.util.Objects.requireNonNull(pcs);
            final String message=new StringBuilder(get()).append(" ").append(msg).toString();
            SC_SystemMessage.broadcastRecipients(pcs,message);
        }""")
    src=swap(src,"public void sendGreenMsgToList(ArrayList<L1PcInstance> pcs){",
        ["for (L1PcInstance pc : pcs) SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, get());"],
        """public void sendGreenMsgToList(ArrayList<L1PcInstance> pcs){
            java.util.Objects.requireNonNull(pcs);
            """+SHARED+""".sendRecipients(pcs,SC_PacketBox.sharedGreenWire(get()));
        }""")
    src=swap(src,"public void sendGreenMsgToList(ArrayList<L1PcInstance> pcs, String msg){",
        ['new StringBuilder(get()).append(" ").append(msg).toString()',
         'for (L1PcInstance pc : pcs) SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, message);'],
        """public void sendGreenMsgToList(ArrayList<L1PcInstance> pcs, String msg){
            java.util.Objects.requireNonNull(pcs);
            final String message=new StringBuilder(get()).append(" ").append(msg).toString();
            """+SHARED+""".sendRecipients(pcs,SC_PacketBox.sharedGreenWire(message));
        }""")
    assert src.count("SC_SystemMessage.broadcastRecipients(pcs,")==2
    assert src.count(SHARED+".sendRecipients(pcs,")==2
    assert "private void sendList(ArrayList<L1PcInstance> pcs, ServerPacket pck)" in src
    return src

TEST=r'''import java.util.Arrays;
import java.nio.charset.Charset;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
public final class LfcNoticeParity12 {
 static int checks=0;
 static void check(boolean b) {if(!b)throw new AssertionError("notice parity "+checks);checks++;}
 static byte[] green(String msg) {
   byte[] text=(msg==null?"":msg).getBytes(Charset.forName("MS949"));
   byte[] w=new byte[text.length+6];w[0]=(byte)w.length;w[1]=(byte)(w.length>>>8);
   w[2]=(byte)Opcodes.S_EVENT;w[3]=(byte)SC_PacketBox.GREEN_MESSAGE;w[4]=2;
   System.arraycopy(text,0,w,5,text.length);return w;
 }
 static byte[] system(String msg) {
   byte[] text=(msg==null?"":msg).getBytes(Charset.forName("MS949"));
   byte[] w=new byte[text.length+5];w[0]=(byte)w.length;w[1]=(byte)(w.length>>>8);
   w[2]=(byte)Opcodes.S_MESSAGE;w[3]=9;
   System.arraycopy(text,0,w,4,text.length);return w;
 }
 public static void main(String[] args) {
   String[] fixed={"","한국어","Start!","[경기준비]  3초 후","\\f2공지",null};
   for(String msg:fixed) {
     check(Arrays.equals(green(msg),SC_PacketBox.sharedGreenWire(msg)));
     check(Arrays.equals(system(msg),SC_SystemMessage.sharedWire(msg)));
   }
   int r=0x626754;
   for(int i=0;i<4096;i++) {
     r^=r<<13;r^=r>>>17;r^=r<<5;
     String msg=i%2==0?"[LFC] 전투 "+r:"경기 종료 "+r;
     check(Arrays.equals(green(msg),SC_PacketBox.sharedGreenWire(msg)));
     check(Arrays.equals(system(msg),SC_SystemMessage.sharedWire(msg)));
   }
   System.out.println("LFC_NOTICE_PARITY12_OK "+checks);
 }
}'''
def main():
    p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase12"))
    args=p.parse_args();raw=SOURCE.read_bytes()
    assert blobsha(raw)==PIN,"phase11 SRC is not verified baseline"
    with zipfile.ZipFile(SOURCE) as z:
        assert z.testzip() is None
        files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
    prior=files.copy()
    assert sum(p.endswith(".java") for p in files)==1711
    assert "CTransport.dll" in files
    files[TARGET]=patch(files[TARGET].decode("utf8")).encode("utf8")
    changed=sorted(p for p in files if files[p]!=prior[p])
    assert changed==[TARGET],changed
    assert files["CTransport.dll"]==prior["CTransport.dll"]
    args.work.mkdir(parents=True,exist_ok=True)
    for p,b in files.items():
        if p.startswith("src/") and p.endswith(".java"):
            dest=args.work/p;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(b)
    tst=args.work/"testsrc/LfcNoticeParity12.java";tst.parent.mkdir(parents=True,exist_ok=True);tst.write_text(TEST)
    OUTPUT.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(OUTPUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for p,b in sorted(files.items()):z.writestr(p,b)
    with zipfile.ZipFile(OUTPUT) as z:
        assert z.testzip() is None and set(z.namelist())==set(files)
    info={"phase":12,"baseline_blob":PIN,
        "sha256":hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),
        "modified_java":changed,"java_sources":1711,
        "four_lfc_list_methods_shared":True,"list_order_and_duplicates_preserved":True,
        "no_legacy_fallback":True,"native_dll_unchanged":True,"runtime_windows_tested":False}
    (args.work/"audit.json").write_text(json.dumps(info,ensure_ascii=False,indent=2)+"\n")
    print(json.dumps(info,ensure_ascii=False,indent=2))
if __name__=="__main__":main()
