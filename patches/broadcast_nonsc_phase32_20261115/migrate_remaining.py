#!/usr/bin/env python3
"""Phase32: remove source-dead legacy clan/party ServerPacket broadcasters,
and migrate remaining repeated green/system/code notices to one shared wire.

Baseline: exact verified Phase31 whole SRC (Git blob SHA pin).
No new workers, queues, compat fallback, packet formats or recipient policies.
"""
from pathlib import Path
import argparse,hashlib,json,re,zipfile
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/"downloads/RKern_Broadcast_NonSC_Phase31_FULL_SRC_20261115.zip"
OUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase32_FULL_SRC_20261115.zip"
PIN="901075630eb0dcc562b5fe36aa4fd626ff396b68"
BASE="src/l1j/server/"
CLAN=BASE+"server/model/L1Clan.java"
PARTY=BASE+"server/model/L1Party.java"
QUEEN=BASE+"IndunSystem/Ice/Queen/Queen.java"
STADIUM=BASE+"Stadium/StadiumObject.java"
ANNOUNCE=BASE+"server/Announcecycle.java"
CHAT=BASE+"server/model/L1ChatParty.java"
MODIFIED=sorted([CLAN,PARTY,QUEEN,STADIUM,ANNOUNCE,CHAT])
BC="l1j.server.server.model.network.core.broadcast"
def blob(b):
 return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def method(src,sig):
 # The legacy L1Clan contains commented-out prior implementations; take last active occurrence.
 at=src.rfind(sig)
 assert at>=0,(sig,"not found")
 assert not src[src.rfind("\n",0,at)+1:at].lstrip().startswith("//"),sig
 opening=src.index("{",at);level=0
 for i in range(opening,len(src)):
  if src[i]=="{":level+=1
  elif src[i]=="}":
   level-=1
   if level==0:return at,i+1,src[at:i+1]
 raise AssertionError(("unbalanced",sig))
def exact_replace(src,sig,new,expected):
 a,b,old=method(src,sig)
 for tok in expected:assert tok in old,(sig,tok)
 assert old!=new
 return src[:a]+new+src[b:]
QUEEN_LOOP=re.compile(
 r"for\s*\(\s*L1Object\s+obj\s*:\s*L1World\.getInstance\(\)\.getVisibleObjects\(_map\)\.values\(\)\s*\)\s*\{\s*"
 r"if\s*\(\s*obj\s+instanceof\s+L1PcInstance\s*\)\s*\{\s*"
 r"L1PcInstance\s+pc\s*=\s*\(L1PcInstance\)\s*obj;\s*"
 r"SC_PacketBox\.send\(\s*pc\s*,\s*SC_PacketBox\.GREEN_MESSAGE\s*,\s*(.*?)\s*\);\s*"
 r"\}\s*\}",re.DOTALL)
def patch(files):
 s=files[CLAN].decode("utf8")
 # Remove only the two ServerPacket and two ProtoOutputStream live broadcast methods.
 for sig,check in (
  ("public void broadcast(ServerPacket pck) {","broadcast(pck, true);"),
  ("public void broadcast(ServerPacket pck, boolean isClear) {","m.player.sendPackets(pck, false);"),
  ("public void broadcast(ProtoOutputStream output) {","broadcast(output, true);"),
  ("public void broadcast(ProtoOutputStream output, boolean isClear) {","m.player.sendPackets(output, false);")
 ):
  a,b,old=method(s,sig)
  assert check in old,(sig,old)
  s=s[:a]+s[b:]
 for sig in ("public void broadcast(ServerPacket pck)", "public void broadcast(ProtoOutputStream output)"):
  assert sig not in s
 # Imports can still be needed elsewhere; don't remove unrelated imports.
 files[CLAN]=s.encode("utf8")
 s=files[PARTY].decode("utf8")
 a,b,old=method(s,"public void broadcastRootMent(ServerPacket packet){")
 assert "pc.sendPackets(packet, false)" in old
 s=s[:a]+s[b:]
 new='''public void broadcastRootMent(String message) {
        if(message==null)return;
        try(var shared=new '''+BC+'''.CAsyncSendConstBuffer(
                SC_SystemMessage.sharedWire(message),
                Integer.MIN_VALUE,Integer.MAX_VALUE)){
            for(L1PcInstance pc:_membersList){
                if(pc!=null && pc.RootMent1)
                    '''+BC+'''.CSharedBroadcast.sendRecipient(shared,pc);
            }
        }
    }'''
 s=exact_replace(s,"public void broadcastRootMent(String message) {",new,
     ["if (message == null) return;", "for (L1PcInstance pc : _membersList)",
       "pc != null && pc.RootMent1", "SC_SystemMessage.send(pc, message)"])
 assert "broadcastRootMent(ServerPacket" not in s
 files[PARTY]=s.encode("utf8")
 s=files[QUEEN].decode("utf8")
 candidates=list(QUEEN_LOOP.finditer(s))
 assert len(candidates)==2,len(candidates)
 for m in candidates:
  text=m.group(1).strip()
  assert "pc." not in text and "SC_" not in text,(QUEEN,text)
 s,n=QUEEN_LOOP.subn(lambda m:
    BC+".CSharedBroadcast.sendObjectPlayers(\n"+
    "                L1World.getInstance().getVisibleObjects(_map).values(),\n"+
    "                SC_PacketBox.sharedGreenWire("+m.group(1).strip()+"));",s)
 assert n==2
 files[QUEEN]=s.encode("utf8")
 s=files[ANNOUNCE].decode("utf8")
 new='''private void ShowAnnounceToAll(String msg) {
        Collection<L1PcInstance> allpc=L1World.getInstance().getAllPlayers();
        '''+BC+'''.CSharedBroadcast.sendRecipients(
                allpc,SC_SystemMessage.sharedWire(msg));
    }'''
 s=exact_replace(s,"private void ShowAnnounceToAll(String msg) {",new,
   ["getAllPlayers()", "for (L1PcInstance pc : allpc)","SC_SystemMessage.send(pc, msg)"])
 files[ANNOUNCE]=s.encode("utf8")
 s=files[STADIUM].decode("utf8")
 for oldcall,frame in (
   ("SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, message);",
    "SC_PacketBox.sharedGreenWire(message)"),
   ("SC_SystemMessage.send(pc, message);",
    "SC_SystemMessage.sharedWire(message)")):
  # Find exact enclosing for-loop, and keep its body intact except the packet send.
  key="for(L1PcInstance pc : col){"
  matches=list(re.finditer(re.escape(key),s))
  assert len(matches)==2,len(matches)
  choice=0 if "GREEN_MESSAGE" in oldcall else 1
  a=matches[choice].start()
  brace=s.index("{",a);dep=0;b=None
  for i in range(brace,len(s)):
   if s[i]=="{":dep+=1
   elif s[i]=="}":
    dep-=1
    if dep==0:b=i+1;break
  assert b is not None
  old=s[a:b]
  assert old.count(oldcall)==1,(choice,old)
  assert "if(!check_pc(pc))" in old
  assert "Proto.sendRaw(pc, " in old,(choice,old)
  replacement=old.replace(oldcall,BC+".CSharedBroadcast.sendRecipient(shared,pc);",1)
  wrapped='''try(var shared=new '''+BC+'''.CAsyncSendConstBuffer(
                '''+frame+''',Integer.MIN_VALUE,Integer.MAX_VALUE)){
            '''+replacement+'''
        }'''
  s=s[:a]+wrapped+s[b:]
 files[STADIUM]=s.encode("utf8")
 s=files[CHAT].decode("utf8")
 sig="public void addMember(L1PcInstance pc) {"
 a,b,old=method(s,sig)
 loop="for(L1PcInstance member : _membersList){"
 assert old.count(loop)==1
 j=old.index(loop);l0=j;l1=old.index("}",j)+1
 fragment=old[l0:l1]
 assert fragment.count("SC_ServerMessage.send(member, 424, pc.getName());")==1,fragment
 repl=BC+'''.CSharedBroadcast.sendRecipients(_membersList,
                SC_ServerMessage.sharedWire(424,pc.getName()));'''
 old=old[:l0]+repl+old[l1:]
 s=s[:a]+old+s[b:]
 a,b,old=method(s,"private void breakup() {")
 assert old.count("SC_ServerMessage.send(member, 418);")==1
 assert "removeMember(member);" in old
 assert "for (L1PcInstance member : getMembers())" in old
 new='''private void breakup() {
        try(var shared=new '''+BC+'''.CAsyncSendConstBuffer(
                SC_ServerMessage.sharedWire(418),
                Integer.MIN_VALUE,Integer.MAX_VALUE)){
            for(L1PcInstance member:getMembers()){
                removeMember(member);
                '''+BC+'''.CSharedBroadcast.sendRecipient(shared,member);
            }
        }
    }'''
 s=s[:a]+new+s[b:]
 files[CHAT]=s.encode("utf8")
 return {"dead_legacy_methods_removed":5,
         "shared_notice_paths":8,"ice_queen":2,"stadium":2,
         "root_ment":1,"chat_party":2,"announcements":1}
TEST=r'''import java.util.Arrays;
import java.nio.charset.Charset;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
public final class Phase32BroadcastWireParity {
 static int count;
 static void eq(byte[] a,byte[] b){
  if(!Arrays.equals(a,b))throw new AssertionError("wire parity "+count);count++;
 }
 static byte[] green(String s){
  byte[] t=s.getBytes(Charset.forName("MS949"));byte[] b=new byte[t.length+6];
  b[0]=(byte)b.length;b[1]=(byte)(b.length>>>8);b[2]=(byte)Opcodes.S_EVENT;
  b[3]=(byte)SC_PacketBox.GREEN_MESSAGE;b[4]=2;
  System.arraycopy(t,0,b,5,t.length);
  return b;
 }
 static byte[] system(String s){
  byte[] t=s.getBytes(Charset.forName("MS949"));byte[] b=new byte[t.length+5];
  b[0]=(byte)b.length;b[1]=(byte)(b.length>>>8);
  b[2]=(byte)Opcodes.S_MESSAGE;b[3]=9;
  System.arraycopy(t,0,b,4,t.length);return b;
 }
 static byte[] server(int code,String s){
  byte[] t=s.getBytes(Charset.forName("MS949"));byte[] b=new byte[t.length+7];
  b[0]=(byte)b.length;b[1]=(byte)(b.length>>>8);
  b[2]=(byte)Opcodes.S_MESSAGE_CODE;b[3]=(byte)code;b[4]=(byte)(code>>>8);
  b[5]=1;System.arraycopy(t,0,b,6,t.length);return b;
 }
 static void check(int code,String s){
  eq(green(s),SC_PacketBox.sharedGreenWire(s));
  eq(system(s),SC_SystemMessage.sharedWire(s));
  eq(server(code,s),SC_ServerMessage.sharedWire(code,s));
 }
 public static void main(String[] args){
  for(String s:new String[]{"","벽 뒤에 있는 스빈을 만나십시오.",
    "10분 후에 마을로 강제 이동 됩니다.","Go!!","혈맹 대화","시작 안내","\\f3F"})
     check(424,s);
  int r=0x13577f25;
  for(int i=0;i<4096;i++){r^=r<<13;r^=r>>>17;r^=r<<5;
     check(r,"[안내] "+r+"초 뒤 시작합니다.");}
  System.out.println("PHASE32_PARITY_OK "+count);
 }
}'''
def main():
 ap=argparse.ArgumentParser()
 ap.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase32"))
 args=ap.parse_args()
 raw=SRC.read_bytes();assert blob(raw)==PIN,"baseline blob changed"
 with zipfile.ZipFile(SRC) as z:
  assert z.testzip() is None
  files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
 before=files.copy()
 assert sum(x.endswith(".java") for x in files)==1710 and "CTransport.dll" in files
 result=patch(files)
 changed=sorted(n for n in files if files[n]!=before[n])
 assert changed==MODIFIED,changed
 assert files["CTransport.dll"]==before["CTransport.dll"]
 args.work.mkdir(parents=True,exist_ok=True)
 for n,data in files.items():
  if n.startswith("src/") and n.endswith(".java"):
   f=args.work/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(data)
 test=args.work/"testsrc/Phase32BroadcastWireParity.java";test.parent.mkdir(parents=True,exist_ok=True);test.write_text(TEST)
 OUT.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for n,data in sorted(files.items()):z.writestr(n,data)
 with zipfile.ZipFile(OUT) as z:
  assert z.testzip() is None and set(z.namelist())==set(files)
 report={"phase":32,"input_blob":PIN,"output_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
   "java_sources":1710,"modified_java":changed,**result,
   "source_dead_only":True,"map_and_order_preserved":True,
   "native_unchanged":True,"no_legacy_fallback":True,
   "new_workers_queues_classes":0,"windows_runtime_tested":False}
 (args.work/"audit.json").write_text(json.dumps(report,indent=2,ensure_ascii=False)+"\n")
 print(json.dumps(report,indent=2,ensure_ascii=False))
if __name__=="__main__":main()
