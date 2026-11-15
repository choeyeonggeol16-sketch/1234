#!/usr/bin/env python3
"""Phase27: Antharas/Fafurion/Lindvior intro message loops share one
immutable native frame per scripted line; HauntedHouse notices also share.
Preserve delayed sequence, map eligibility, recipient traversal and duplicates.
"""
from pathlib import Path
import argparse,hashlib,json,re,zipfile
ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/"downloads/RKern_Broadcast_NonSC_Phase26_FULL_SRC_20261115.zip"
OUTPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase27_FULL_SRC_20261115.zip"
PIN="3f94c3d27db7cb5cc05d682d58a9502f8d927292"
BASE="src/l1j/server/"
ANT=BASE+"MJRaidSystem/Objects/MJRaidAntaras.java"
FAF=BASE+"MJRaidSystem/Objects/MJRaidFafurion.java"
RIN=BASE+"MJRaidSystem/Objects/MJRaidRindvior.java"
HAUNT=BASE+"server/model/L1HauntedHouse.java"
TARGETS=[ANT,FAF,RIN,HAUNT]
FQ="l1j.server.server.model.network.core.broadcast"
def sha(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def bounds(src,sig):
    assert src.count(sig)==1,(sig,src.count(sig))
    a=src.index(sig);b=src.index("{",a);depth=0
    for k in range(b,len(src)):
        if src[k]=="{":depth+=1
        elif src[k]=="}":
            depth-=1
            if depth==0:return a,k+1,src[a:k+1]
    raise AssertionError(sig)
def patch(files):
    for path in (ANT,FAF,RIN):
        s=files[path].decode("utf8")
        outer="for(int i=0; i<=s_startMessages.length; i++){"
        a,b,old=bounds(s,outer)
        j0,j1,inner=bounds(old,"for(int j=0; j<size; j++){")
        one="if (i < s_startMessages.length) SC_ServerMessage.send(pc, s_startMessages[i]);"
        last="else SC_SystemMessage.send(pc, s_lastMessage);"
        assert inner.count(one)==1 and inner.count(last)==1,(path,inner)
        assert "pc = _users.get(j);" in inner and "pc.getMapId() == cpMap" in inner
        inner=inner.replace(one+"\n", "",1)
        assert last in inner
        inner=inner.replace(last,FQ+".CSharedBroadcast.sendRecipient(shared,pc);",1)
        wrapper='''try(var shared=new '''+FQ+'''.CAsyncSendConstBuffer(
                  i<s_startMessages.length
                    ? SC_ServerMessage.sharedWire(s_startMessages[i])
                    : SC_SystemMessage.sharedWire(s_lastMessage),
                  Integer.MIN_VALUE,Integer.MAX_VALUE)){
                 '''+inner+'''
             }'''
        old=old[:j0]+wrapper+old[j1:]
        s=s[:a]+old+s[b:]
        assert "SC_ServerMessage.send(pc, s_startMessages[i])" not in s
        files[path]=s.encode("utf8")
    s=files[HAUNT].decode("utf8")
    a,b,old=bounds(s,"private void broadcast(String msg) {")
    for needle in ("for (L1PcInstance pc : getMembersArray())", "pc.getMapId() == 5140",
                   "SC_SystemMessage.send(pc, msg);"):
        assert needle in old,(needle,old)
    new='''private void broadcast(String msg) {
        try(var shared=new '''+FQ+'''.CAsyncSendConstBuffer(
                SC_SystemMessage.sharedWire(msg),Integer.MIN_VALUE,Integer.MAX_VALUE)){
            for(L1PcInstance pc:getMembersArray()){
                if(pc.getMapId()==5140)
                    '''+FQ+'''.CSharedBroadcast.sendRecipient(shared,pc);
            }
        }
    }'''
    s=s[:a]+new+s[b:]
    a,b,old=bounds(s,"private boolean readyHauntedHouse() {")
    pattern=re.compile(
        r"for\s*\(\s*L1PcInstance\s+pc\s*:\s*getMembersArray\(\)\s*\)\s*\{\s*"
        r"SC_ServerMessage\.send\(pc,\s*1257\);\s*\}")
    assert len(pattern.findall(old))==1
    new,n=pattern.subn(
        FQ+".CSharedBroadcast.sendArray(getMembersArray(),SC_ServerMessage.sharedWire(1257));",old)
    assert n==1 and "pc.getInventory().storeItem(40308, 1000)" in new
    s=s[:a]+new+s[b:]
    files[HAUNT]=s.encode("utf8")
TEST=r'''import java.util.Arrays;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.*;
public final class Phase27RaidHauntParity {
 static int checked;
 static void eq(byte[] a,byte[] b){if(!Arrays.equals(a,b))throw new AssertionError(checked);checked++;}
 static byte[] type(int id){
  return ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
   .putShort((short)6).put((byte)Opcodes.S_MESSAGE_CODE)
   .putShort((short)id).put((byte)0).array();
 }
 static byte[] msg(String s){
  byte[] t=s.getBytes(Charset.forName("MS949"));byte[] b=new byte[t.length+5];
  b[0]=(byte)b.length;b[1]=(byte)(b.length>>>8);
  b[2]=(byte)Opcodes.S_MESSAGE;b[3]=9;System.arraycopy(t,0,b,4,t.length);
  return b;
 }
 static void check(int id,String s){
  eq(type(id),SC_ServerMessage.sharedWire(id));
  eq(msg(s),SC_SystemMessage.sharedWire(s));
 }
 public static void main(String[] args){
  for(String s:new String[]{"","레이드","[안타라스]","[파푸리온]","[린드비오르]","마감 안내"})
    for(int id:new int[]{0,1257,1270,65535,-1})check(id,s);
  int r=0x3434abcd;
  for(int i=0;i<4096;i++){r^=r<<13;r^=r>>>17;r^=r<<5;check(r,"Raid-"+r);}
  System.out.println("PHASE27_RAID_HAUNT_PARITY_OK "+checked);
 }
}'''
def main():
    p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase27"));args=p.parse_args()
    raw=SOURCE.read_bytes();assert sha(raw)==PIN,"phase26 source mismatch"
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
    for p,data in files.items():
        if p.startswith("src/") and p.endswith(".java"):
            f=args.work/p;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(data)
    test=args.work/"testsrc/Phase27RaidHauntParity.java";test.parent.mkdir(parents=True,exist_ok=True);test.write_text(TEST)
    OUTPUT.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(OUTPUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for p,data in sorted(files.items()):z.writestr(p,data)
    with zipfile.ZipFile(OUTPUT) as z:assert z.testzip() is None and set(z.namelist())==set(files)
    audit={"phase":27,"input_blob":PIN,"output_sha256":hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),
      "java_sources":1710,"modified_java":changed,
      "dragon_raid_line_broadcast_loops":3,"haunted_house_notice_loops":2,
      "raid_map_filter_and_script_delay_preserved":True,
      "haunted_member_order_and_map_filter_preserved":True,
      "native_unchanged":True,"no_fallback":True,"no_new_threads_or_queues":True,
      "windows_runtime_tested":False}
    (args.work/"audit.json").write_text(json.dumps(audit,ensure_ascii=False,indent=2)+"\n")
    print(json.dumps(audit,ensure_ascii=False,indent=2))
if __name__=="__main__":main()
