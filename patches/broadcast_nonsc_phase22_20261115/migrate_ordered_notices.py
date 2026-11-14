#!/usr/bin/env python3
"""Phase22: keep per-recipient side effects/ordered countdown traversal, but
share plaintext source for raid countdown and companion training reset.
The new sendRecipient helper does not reorder target sends or copy recipients.
"""
from pathlib import Path
import hashlib,json,zipfile,argparse
ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/"downloads/RKern_Broadcast_NonSC_Phase21_FULL_SRC_20261115.zip"
OUTPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase22_FULL_SRC_20261115.zip"
PIN="aa4b1d70e6eec8f0b4098c60a323593f874bfee5"
BASE="src/l1j/server/"
SHARED=BASE+"server/model/network/core/broadcast/CSharedBroadcast.java"
RAID=BASE+"MJRaidSystem/MJRaidObject.java"
COMPANION=BASE+"MJCompanion/Instance/MJCompanionRegenerator.java"
TARGETS=[SHARED,RAID,COMPANION]
CLASS="l1j.server.server.model.network.core.broadcast"
def sha(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def block(src,at):
    j=src.index("{",at);depth=0
    for i in range(j,len(src)):
        if src[i]=="{":depth+=1
        elif src[i]=="}":
            depth-=1
            if depth==0:return at,i+1,src[at:i+1]
    raise AssertionError("unterminated")
def patch(files):
    s=files[SHARED].decode("utf8")
    marker="    /** Array event recipients, no temporary iterable"
    assert s.count(marker)==1
    add=r'''    /** One recipient of an already opened native shared frame. Allows
     * side effects or other packets between fanout recipients without
     * changing per-client FIFO ordering or allocating new lists.
     */
    public static void sendRecipient(CAsyncSendConstBuffer shared,L1PcInstance pc) {
        if(shared==null || pc==null)return;
        final CIOClient client=pc.getNetConnection();
        if(client==null || !client.isConnected())return;
        final var socket=client.getConnection();
        if(socket!=null && socket.isClientConnected())
            shared.send(socket,pc.getId());
    }

'''
    files[SHARED]=s.replace(marker,add+marker,1).encode("utf8")
    s=files[RAID].decode("utf8")
    anchor='pck = String.format("%d초 후 마을로 이동합니다.", allTime / 1000);'
    assert s.count(anchor)==1
    start=s.index("for(int i=0; i<size; i++){",s.index(anchor))
    a,b,old=block(s,start)
    assert old.count("SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, pck);")==1
    rewritten=old.replace("SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, pck);",
        CLASS+".CSharedBroadcast.sendRecipient(shared,pc);")
    replacement="""try(var shared=new """+CLASS+""".CAsyncSendConstBuffer(
                    SC_PacketBox.sharedGreenWire(pck),Integer.MIN_VALUE,Integer.MAX_VALUE)){
"""+rewritten+"\n                }"
    files[RAID]=(s[:a]+replacement+s[b:]).encode("utf8")
    s=files[COMPANION].decode("utf8")
    anchor='String message = "펫 훈련시간이 초기화 되었습니다.";'
    assert s.count(anchor)==1
    start=s.index("for(L1PcInstance pc : L1World.getInstance().getAllPlayers()){",s.index(anchor))
    a,b,old=block(s,start)
    assert old.count("SC_SystemMessage.send(pc, message);")==1
    rewritten=old.replace("SC_SystemMessage.send(pc, message);",
        CLASS+".CSharedBroadcast.sendRecipient(shared,pc);")
    replacement="""try(var shared=new """+CLASS+""".CAsyncSendConstBuffer(
                    SC_SystemMessage.sharedWire(message),Integer.MIN_VALUE,Integer.MAX_VALUE)){
"""+rewritten+"\n                }"
    files[COMPANION]=(s[:a]+replacement+s[b:]).encode("utf8")
TEST=r'''import java.nio.charset.Charset;
import java.util.Arrays;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.*;
public final class Phase22NoticeParity {
 static int checks;
 static void eq(byte[] a,byte[] b){if(!Arrays.equals(a,b))throw new AssertionError(checks);checks++;}
 static byte[] system(String s){
  byte[] text=s.getBytes(Charset.forName("MS949"));
  byte[] w=new byte[text.length+5];
  w[0]=(byte)w.length;w[1]=(byte)(w.length>>>8);
  w[2]=(byte)Opcodes.S_MESSAGE;w[3]=9;
  System.arraycopy(text,0,w,4,text.length);
  return w;
 }
 static byte[] green(String s){
  byte[] text=s.getBytes(Charset.forName("MS949"));
  byte[] w=new byte[text.length+6];
  w[0]=(byte)w.length;w[1]=(byte)(w.length>>>8);
  w[2]=(byte)Opcodes.S_EVENT;w[3]=(byte)SC_PacketBox.GREEN_MESSAGE;w[4]=2;
  System.arraycopy(text,0,w,5,text.length);
  return w;
 }
 static void check(String s){eq(system(s),SC_SystemMessage.sharedWire(s));eq(green(s),SC_PacketBox.sharedGreenWire(s));}
 public static void main(String[] args){
  for(String s:new String[]{"","펫 훈련시간이 초기화 되었습니다.","20초 후 마을로 이동합니다.","한글",""})
     check(s);
  for(int i=0;i<4096;i++)check(i+"초 후 마을로 이동합니다.");
  System.out.println("PHASE22_NOTICE_PARITY_OK "+checks);
 }
}'''
def main():
    parser=argparse.ArgumentParser();parser.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase22"));args=parser.parse_args()
    b=SOURCE.read_bytes()
    assert sha(b)==PIN,"phase21 source mismatch"
    with zipfile.ZipFile(SOURCE) as z:
        assert z.testzip() is None
        files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
    old=files.copy()
    assert sum(n.endswith(".java") for n in files)==1710 and "CTransport.dll" in files
    patch(files)
    changed=sorted(p for p in files if files[p]!=old[p])
    assert changed==sorted(TARGETS),changed
    assert files["CTransport.dll"]==old["CTransport.dll"]
    args.work.mkdir(parents=True,exist_ok=True)
    for path,buf in files.items():
        if path.startswith("src/") and path.endswith(".java"):
            f=args.work/path;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(buf)
    f=args.work/"testsrc/Phase22NoticeParity.java";f.parent.mkdir(parents=True,exist_ok=True);f.write_text(TEST)
    OUTPUT.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(OUTPUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for p,buf in sorted(files.items()):z.writestr(p,buf)
    with zipfile.ZipFile(OUTPUT) as z:
        assert z.testzip() is None and set(z.namelist())==set(files)
    report={"phase":22,"input_blob":PIN,"output_sha256":hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),
            "java_sources":1710,"modified_java":changed,
            "raid_countdown_shared":True,"companion_reset_shared":True,
            "original_indexed_raid_iteration_preserved":True,
            "companion_mutation_before_send_preserved":True,
            "native_unchanged":True,"no_queue_thread_added":True,"windows_runtime_tested":False}
    (args.work/"audit.json").write_text(json.dumps(report,indent=2)+"\n")
    print(json.dumps(report,indent=2))
if __name__=="__main__":main()
