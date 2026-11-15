#!/usr/bin/env python3
"""Phase26: identical event fanouts reuse one frozen frame per message.
Keep racing group traversal, DeathMatch array order, GM filter, and Valakas
effect/sleep/raid ordering. No recipient deduplication, queues, or fallbacks.
"""
from pathlib import Path
import argparse,hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/"downloads/RKern_Broadcast_NonSC_Phase25_FULL_SRC_20261115.zip"
OUTPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase26_FULL_SRC_20261115.zip"
PIN="738171f1208ce804a655b0bbf63966d3fc34d981"
BASE="src/l1j/server/"
RACING=BASE+"server/model/L1Racing.java"
DEATH=BASE+"server/model/L1DeathMatch.java"
BOSS=BASE+"server/Controller/BossController.java"
VALAKAS=BASE+"MJRaidSystem/Objects/MJRaidValakas.java"
TARGETS=[RACING,DEATH,BOSS,VALAKAS]
FQ="l1j.server.server.model.network.core.broadcast"
def sha(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def method(src,sig):
    assert src.count(sig)==1,(sig,src.count(sig))
    a=src.index(sig);b=src.index("{",a);depth=0
    for k in range(b,len(src)):
        if src[k]=="{":depth+=1
        elif src[k]=="}":
            depth-=1
            if depth==0:return a,k+1,src[a:k+1]
    raise AssertionError(sig)
def replace(src,sig,new,expected):
    a,b,old=method(src,sig)
    for frag in expected:assert frag in old,(sig,frag)
    assert old!=new
    return src[:a]+new+src[b:]
def patch(files):
    src=files[RACING].decode("utf8")
    sig="private void broadcast(String msg)"
    new='''private void broadcast(String msg)
    {
        try(var shared=new '''+FQ+'''.CAsyncSendConstBuffer(
                SC_SystemMessage.sharedWire(msg),Integer.MIN_VALUE,Integer.MAX_VALUE)){
            for(int i=0;i<18;i++){
                for(L1PcInstance pc:toArray(i)){
                    if(pc.getMapId()==getMapId())
                        '''+FQ+'''.CSharedBroadcast.sendRecipient(shared,pc);
                }
            }
        }
    }'''
    src=replace(src,sig,new,
        ["for(int i = 0; i < 18; i++)","for (L1PcInstance pc : toArray(i))",
         "if (pc.getMapId() == getMapId())","SC_SystemMessage.send(pc, msg)"])
    files[RACING]=src.encode("utf8")
    src=files[DEATH].decode("utf8")
    sig="public void sendMessage(int type, String msg) {"
    new='''public void sendMessage(int type, String msg) {
        '''+FQ+'''.CSharedBroadcast.sendArray(
                getMembersArray(),SC_ServerMessage.sharedWire(type,msg));
    }'''
    src=replace(src,sig,new,
        ["for (L1PcInstance pc : getMembersArray())", "SC_ServerMessage.send(pc, type, msg)"])
    files[DEATH]=src.encode("utf8")
    src=files[BOSS].decode("utf8")
    sig="private static void notifyGm(final String msg) {"
    new='''private static void notifyGm(final String msg) {
        '''+FQ+'''.CSharedBroadcast.sendAllGms(SC_SystemMessage.sharedWire(msg));
    }'''
    src=replace(src,sig,new,
       ["L1World.getInstance().getAllPlayers()", "pc != null && pc.isGm()",
        "SC_SystemMessage.send(pc, msg)"])
    files[BOSS]=src.encode("utf8")
    src=files[VALAKAS].decode("utf8")
    for arrayname in ("s_startMessages","s_startMessagesValakas"):
        outer="for(int i=0; i<"+arrayname+".length; i++){"
        a,b,whole=method(src,outer)
        direct="SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "+arrayname+"[i]);"
        assert whole.count(direct)==1,(outer,whole.count(direct))
        j0,j1,inner=method(whole,"for(int j=0; j<size; j++){")
        assert "pc = _users.get(j);" in inner
        assert "pc.getMapId() == mid" in inner
        if arrayname=="s_startMessagesValakas":
            assert "Thread.sleep(500);" in inner and "SC_DisplayEffect.send(pc, s_valaksDisplay);" in inner
        inner=inner.replace(direct,
             FQ+".CSharedBroadcast.sendRecipient(shared,pc);",1)
        wrapper='''try(var shared=new '''+FQ+'''.CAsyncSendConstBuffer(
                    SC_PacketBox.sharedGreenWire('''+arrayname+'''[i]),
                    Integer.MIN_VALUE,Integer.MAX_VALUE)){
                    '''+inner+'''
                }'''
        whole=whole[:j0]+wrapper+whole[j1:]
        src=src[:a]+whole+src[b:]
    assert "SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, s_startMessages" not in src
    files[VALAKAS]=src.encode("utf8")
TEST=r'''import java.util.Arrays;
import java.nio.charset.Charset;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.*;
public final class Phase26EventWireParity {
 static int checks;
 static void eq(byte[] a,byte[] b){if(!Arrays.equals(a,b))throw new AssertionError(checks);checks++;}
 static byte[] green(String s){
  byte[] t=s.getBytes(Charset.forName("MS949"));byte[] w=new byte[t.length+6];
  w[0]=(byte)w.length;w[1]=(byte)(w.length>>>8);
  w[2]=(byte)Opcodes.S_EVENT;w[3]=(byte)SC_PacketBox.GREEN_MESSAGE;w[4]=2;
  System.arraycopy(t,0,w,5,t.length);return w;
 }
 static byte[] system(String s){
  byte[] t=s.getBytes(Charset.forName("MS949"));byte[] w=new byte[t.length+5];
  w[0]=(byte)w.length;w[1]=(byte)(w.length>>>8);w[2]=(byte)Opcodes.S_MESSAGE;w[3]=9;
  System.arraycopy(t,0,w,4,t.length);return w;
 }
 static void check(String s){
  eq(green(s),SC_PacketBox.sharedGreenWire(s));
  eq(system(s),SC_SystemMessage.sharedWire(s));
 }
 public static void main(String[] args){
  for(String s:new String[]{"","레이드 시작","[발라카스]","펫 레이싱","보스 이벤트","\\f2Hi"})check(s);
  int r=0x678fdac;
  for(int i=0;i<4096;i++){r^=r<<13;r^=r>>>17;r^=r<<5;check("Raid"+r);}
  System.out.println("PHASE26_EVENT_WIRE_PARITY_OK "+checks);
 }
}'''
def main():
    p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase26"));args=p.parse_args()
    raw=SOURCE.read_bytes();assert sha(raw)==PIN,"phase25 baseline mismatch"
    with zipfile.ZipFile(SOURCE) as z:
        assert z.testzip() is None
        files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
    original=files.copy()
    assert sum(n.endswith(".java") for n in files)==1710 and "CTransport.dll" in files
    patch(files)
    changed=sorted(p for p in files if original[p]!=files[p])
    assert changed==sorted(TARGETS),changed
    assert files["CTransport.dll"]==original["CTransport.dll"]
    args.work.mkdir(parents=True,exist_ok=True)
    for p,data in files.items():
        if p.startswith("src/") and p.endswith(".java"):
            f=args.work/p;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(data)
    f=args.work/"testsrc/Phase26EventWireParity.java";f.parent.mkdir(parents=True,exist_ok=True);f.write_text(TEST)
    OUTPUT.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(OUTPUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for p,data in sorted(files.items()):z.writestr(p,data)
    with zipfile.ZipFile(OUTPUT) as z:assert z.testzip() is None and set(z.namelist())==set(files)
    audit={"phase":26,"input_blob":PIN,"output_sha256":hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),
      "java_sources":1710,"modified_java":changed,
      "racing_broadcast_loops":1,"deathmatch_broadcast_loops":1,
      "gm_notice_loops":1,"valakas_message_loops":2,
      "racing_index_and_map_filter_preserved":True,"deathmatch_array_order_preserved":True,
      "valakas_interleaved_effects_sleep_preserved":True,
      "native_unchanged":True,"no_fallback":True,"no_new_workers_or_queues":True,
      "windows_runtime_tested":False}
    (args.work/"audit.json").write_text(json.dumps(audit,ensure_ascii=False,indent=2)+"\n")
    print(json.dumps(audit,ensure_ascii=False,indent=2))
if __name__=="__main__":main()
