#!/usr/bin/env python3
"""Phase51: remove per-recipient wire encoding in SC_ChatPacket world/visible
fanouts, two gambling games, Orim repeated-self-and-member chat, and KDA
plain/colored kill notifications. No new workers, queues, fallback or classes.
"""
from pathlib import Path
import argparse,hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/"downloads/RKern_Broadcast_NonSC_Phase50_FULL_SRC_20261116.zip"
OUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase51_FULL_SRC_20261116.zip"
PIN="06fb9edcd8205842c6a8920b6e56c696ea38de47"
BASE="src/l1j/server/"
CHAT=BASE+"server/model/network/core/sc/packet/SC_ChatPacket.java"
GAMBLE=BASE+"IndunSystem/MiniGame/L1Gambling.java"
GAMBLE3=BASE+"IndunSystem/MiniGame/L1Gambling3.java"
ORIM=BASE+"IndunSystem/Orim/OrimController.java"
SHOW=BASE+"server/model/network/core/sc/packet/SC_ShowCmd.java"
KDA=BASE+"MJKDASystem/MJKDA.java"
TARGETS=sorted([CHAT,GAMBLE,GAMBLE3,ORIM,SHOW,KDA])
BC="l1j.server.server.model.network.core.broadcast"
def blob(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def block(s,signature,start=0):
 i=s.find(signature,start)
 assert i>=0,(signature,start)
 op=s.index("{",i)
 depth=0
 for k in range(op,len(s)):
  if s[k]=="{":depth+=1
  elif s[k]=="}":
   depth-=1
   if depth==0:return i,k+1,s[i:k+1]
 raise AssertionError(signature)
CHAT_HELPER=r'''
    /** Full immutable frame matching MODE_MSG_TYPE1, MODE_OPCODE_TYPE2,
     * MODE_SAY_TEST and MODE_COMPLEX_0: S_*(opcode), C(type),
     * optional D(objectId), MS949 nul-terminated text. Native transport
     * applies receiver-specific encryption after enqueue.
     */
    public static byte[] sharedWire(int opcode,int type,int objectId,
                                    String text,boolean includeObjectId) {
        final byte[] txt=(text==null?"":text).getBytes(
                java.nio.charset.Charset.forName("MS949"));
        final long needed=5L+txt.length+(includeObjectId?4L:0L);
        if(needed>0xffffL)return null;
        final int size=(int)needed;
        final byte[] wire=new byte[size];
        wire[0]=(byte)size;
        wire[1]=(byte)(size>>>8);
        wire[2]=(byte)opcode;
        wire[3]=(byte)type;
        int at=4;
        if(includeObjectId){
            wire[at++]=(byte)objectId;
            wire[at++]=(byte)(objectId>>>8);
            wire[at++]=(byte)(objectId>>>16);
            wire[at++]=(byte)(objectId>>>24);
        }
        System.arraycopy(txt,0,wire,at,txt.length);
        return wire;
    }

'''
SHOW_HELPER=r'''
    /** Exact mode PK S_EXTENDED_PROTOBUF 0x247 frame, including every
     * original one-byte field and LE length/trailer. Names preencoded in
     * MS949 with the existing SC_ShowCmd.encode().
     */
    public static byte[] sharedPkWire(byte[] killer,byte[] victim) {
        if(killer==null || victim==null ||
                killer.length>255 || victim.length>255)return null;
        final int n=17+killer.length+victim.length;
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(n)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)n);
        b.put((byte)Opcodes.S_EXTENDED_PROTOBUF);
        b.putShort((short)0x247);
        b.put((byte)0x08).put((byte)0).put((byte)0x12)
         .put((byte)killer.length).put(killer);
        b.put((byte)0x18).put((byte)0).put((byte)0x22)
         .put((byte)victim.length).put(victim);
        b.put((byte)0x28).put((byte)1).putShort((short)0);
        if(b.position()!=n)throw new IllegalStateException("PK frame size");
        return b.array();
    }

'''
def patch_chat(files):
 s=files[CHAT].decode("utf8")
 anchor="    private static boolean sendDirectRaw(L1PcInstance recipient, int mode, int opcode,"
 assert s.count(anchor)==1 and "public static byte[] sharedWire(" not in s
 for check in ("case MODE_COMPLEX_0:","buffer.writeD(objectId);","buffer.writeS(text1);",
               "case MODE_OPCODE_TYPE2:","case MODE_SAY_TEST:"):
  assert check in s,check
 s=s.replace(anchor,CHAT_HELPER+anchor,1)
 for sig,old,params in (
  ("public static void broadcastMessage(String chat) {",
   "sendDirectRaw(receiver, MODE_MSG_TYPE1, Opcodes.S_MESSAGE, 1, 0, safe, \"\", 0, 0);",
   "Opcodes.S_MESSAGE,1,0,safe,false"),
  ("public static void broadcastOpcode(String chat, int opcode) {",
   "sendDirectRaw(receiver, MODE_OPCODE_TYPE2, opcode, 2, 0, safe, \"\", 0, 0);",
   "opcode,2,0,safe,false"),
 ):
  a,b,part=block(s,sig)
  assert part.count(old)==1,old
  assert "L1World.getInstance().getAllPlayers()" in part
  for_sig="for (L1PcInstance receiver : L1World.getInstance().getAllPlayers()) {"
  i,j,loop=block(part,for_sig)
  assert old in loop
  part=(part[:i]+BC+".CSharedBroadcast.sendRecipients(\n"+
       "L1World.getInstance().getAllPlayers(),sharedWire("+params+"));"+part[j:])
  s=s[:a]+part+s[b:]
 a,b,part=block(s,"public static void broadcastComplex0(L1PcInstance source, String chat, int opcode) {")
 needle="sendDirectRaw(receiver, MODE_COMPLEX_0, opcode, 0, objectId, text, \"\", 0, 0);"
 assert part.count(needle)==1
 assert "borrowVisiblePlayerFast(source)" in part and "releaseVisiblePlayerFast(__cioVisible0)" in part
 i,j,loop=block(part,"for (L1PcInstance receiver : __cioVisible0) {")
 assert needle in loop
 updated=(part[:i]+BC+".CSharedBroadcast.sendRecipients(__cioVisible0,\n"+
       "sharedWire(opcode,0,objectId,text,true));"+part[j:])
 s=s[:a]+updated+s[b:]
 for needle in ('sendDirectRaw(receiver, MODE_MSG_TYPE1',
                'sendDirectRaw(receiver, MODE_OPCODE_TYPE2',
                'sendDirectRaw(receiver, MODE_COMPLEX_0, opcode, 0, objectId, text,'):
  assert needle not in s,needle
 files[CHAT]=s.encode("utf8")
def patch_gambling(files,path,method):
 s=files[path].decode("utf8")
 a,b,part=block(s,method)
 original="SC_ChatPacket.sendComplex0(pc, pc, chatText, Opcodes.S_SAY);"
 call="SC_ChatPacket.sendComplex0(listner, pc, chatText, Opcodes.S_SAY);"
 loop_sig="for (L1PcInstance listner : L1World.getInstance().getRecognizePlayer(pc)) {"
 assert part.count(original)==1 and part.count(call)==1,(path,part.count(original),part.count(call))
 assert "pc.setGambling" in part and "ThreadPool.getInstance().execute" in part
 start=part.index(original)
 i,j,loop=block(part,loop_sig,start)
 assert loop.count(call)==1
 transformed=loop.replace(call,BC+".CSharedBroadcast.sendRecipient(chatShared,listner);",1)
 setup='''final String senderName=pc.getName();
        final byte[] chatWire=SC_ChatPacket.sharedWire(Opcodes.S_SAY,0,pc.getId(),
                    (senderName==null?"":senderName)+": "+(chatText==null?"":chatText),true);
        try(var chatShared=chatWire==null?null:new '''+BC+'''.CAsyncSendConstBuffer(
                    chatWire,Integer.MIN_VALUE,Integer.MAX_VALUE)){
            '''+BC+'''.CSharedBroadcast.sendRecipient(chatShared,pc);
            '''+transformed+'''
        }'''
 part=part[:start]+setup+part[j:]
 assert original not in part and call not in part
 s=s[:a]+part+s[b:]
 files[path]=s.encode("utf8")
def patch_orim(files):
 s=files[ORIM].decode("utf8")
 a,b,method=block(s,"private void talkPartyleader(String msg) {")
 assert "checkMember();" in method
 sig="for (L1PcInstance member : L1World.getInstance().getVisiblePlayer(pc)) {"
 i,j,loop=block(method,sig)
 oldself="SC_ChatPacket.sendDirect(pc, pc, msg, 0);"
 oldmember="SC_ChatPacket.sendDirect(member, pc, msg, 0);"
 assert loop.count(oldself)==1 and loop.count(oldmember)==1
 assert "if (pc.getParty().isLeader(pc))" in method[:i]
 # Crucial: the original sent the SAME packet to the leader once per visible
 # member. Preserve that repetition, and self-before-member for each iteration.
 changed=loop.replace(oldself,BC+".CSharedBroadcast.sendRecipient(shared,pc);",1)
 changed=changed.replace(oldmember,BC+".CSharedBroadcast.sendRecipient(shared,member);",1)
 wrapped='''final byte[] chatWire=SC_ChatPacket.sharedWire(
                      l1j.server.server.Opcodes.S_SAY,15,pc.getId(),msg,true);
            try(var shared=chatWire==null?null:new '''+BC+'''.CAsyncSendConstBuffer(
                      chatWire,Integer.MIN_VALUE,Integer.MAX_VALUE)){
            '''+changed+'''
            }'''
 before=method[:i]
 assert before.count("if (pc.getParty().isLeader(pc))")==1
 before=before.replace("if (pc.getParty().isLeader(pc))",
                       "if (pc.getParty().isLeader(pc)) {",1)
 method=before+wrapped+"\n}"+method[j:]
 s=s[:a]+method+s[b:];files[ORIM]=s.encode("utf8")
def patch_pk(files):
 s=files[SHOW].decode("utf8")
 anchor="    private static boolean send(L1PcInstance pc, int mode, byte[] a, byte[] b, int sec, int opcode) {"
 assert s.count(anchor)==1 and "public static byte[] sharedPkWire(" not in s
 for needle in ("case PK:","out.writeC(0x08); out.writeC(0); out.writeC(0x12); writeS2(out, a);",
                "out.writeC(0x18); out.writeC(0); out.writeC(0x22); writeS2(out, b);"):
  assert needle in s,needle
 files[SHOW]=s.replace(anchor,SHOW_HELPER+anchor,1).encode("utf8")
 s=files[KDA].decode("utf8")
 a,b,method=block(s,"public void notifyKillForGotten(L1PcInstance killer, L1PcInstance victim){")
 for val in ("final byte[] plain = SC_ShowCmd.encode(killer.getName());",
             "final byte[] victimBytes = SC_ShowCmd.encode(victim.getName());",
             "final byte[] colored = SC_ShowCmd.encode(String.format"):
  assert val in method,(KDA,val)
 sig="for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {"
 first=method.index(sig)
 i,j,loop1=block(method,sig,first)
 second=method.index(sig,j)
 k,l,loop2=block(method,sig,second)
 old1="SC_ShowCmd.sendPk(pc, plain, victimBytes);"
 old2="SC_ShowCmd.sendPk(pc, pc.getClanid() == cid ? colored : plain, victimBytes);"
 assert loop1.count(old1)==1 and loop2.count(old2)==1
 assert "pc != null" in loop1 and "if (pc == null) continue;" in loop2
 new1=loop1.replace(old1,BC+".CSharedBroadcast.sendRecipient(plainShared,pc);",1)
 new2=loop2.replace(old2,
    BC+".CSharedBroadcast.sendRecipient(pc.getClanid()==cid?coloredShared:plainShared,pc);",1)
 # Nested try scope: two different branches, plain for both and colored only
 # after clan filter. Early return still closes plainShared via try-with-resource.
 left=method[:i];middle=method[j:k];right=method[l:]
 assert "return;" in middle and "final byte[] colored" in middle
 # Insert plainShared creation after victimBytes snapshot (before cid branch).
 needle="final byte[] victimBytes = SC_ShowCmd.encode(victim.getName());"
 assert left.count(needle)==1
 left=left.replace(needle,needle+"\n"+
  "        try(var plainShared=new "+BC+".CAsyncSendConstBuffer(\n"+
  "                     SC_ShowCmd.sharedPkWire(plain,victimBytes),Integer.MIN_VALUE,Integer.MAX_VALUE)){",1)
 colored_anchor="final byte[] colored = SC_ShowCmd.encode(String.format(\"\\\\aG%s\", killer.getName()));"
 assert colored_anchor in middle,("colored marker",middle[:500])
 middle=middle.replace(colored_anchor,colored_anchor+
  "\n        try(var coloredShared=new "+BC+".CAsyncSendConstBuffer(\n"+
  "                   SC_ShowCmd.sharedPkWire(colored,victimBytes),Integer.MIN_VALUE,Integer.MAX_VALUE)){",1)
 newmethod=left+new1+middle+new2+"\n}"+right
 # Close plain after colored frame is closed, before original method end.
 close=newmethod.rfind("}")
 assert close>=0
 newmethod=newmethod[:close]+"\n}"+newmethod[close:]
 assert old1 not in newmethod and old2 not in newmethod
 s=s[:a]+newmethod+s[b:];files[KDA]=s.encode("utf8")
TEST=r'''import java.util.Arrays;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.*;
public final class Phase51ChatPkParity {
 static final Charset MS949=Charset.forName("MS949");
 static int checks;
 static void eq(byte[] expected,byte[] actual){
  if(!Arrays.equals(expected,actual))throw new AssertionError("wire "+checks);
  checks++;
 }
 static byte[] chat(int opcode,int type,int id,String s,boolean obj) {
  byte[] raw=s.getBytes(MS949);
  ByteBuffer b=ByteBuffer.allocate(5+raw.length+(obj?4:0))
       .order(ByteOrder.LITTLE_ENDIAN);
  b.putShort((short)b.capacity()).put((byte)opcode).put((byte)type);
  if(obj)b.putInt(id);
  b.put(raw).put((byte)0);
  return b.array();
 }
 static byte[] pk(byte[] a,byte[] b){
  ByteBuffer wire=ByteBuffer.allocate(17+a.length+b.length).order(ByteOrder.LITTLE_ENDIAN);
  wire.putShort((short)wire.capacity()).put((byte)Opcodes.S_EXTENDED_PROTOBUF)
       .putShort((short)0x247).put((byte)8).put((byte)0)
       .put((byte)0x12).put((byte)a.length).put(a)
       .put((byte)0x18).put((byte)0).put((byte)0x22)
       .put((byte)b.length).put(b).put((byte)0x28)
       .put((byte)1).putShort((short)0);
  if(wire.position()!=wire.capacity())throw new AssertionError("PK length");
  return wire.array();
 }
 static void test(int n,String msg){
  eq(chat(Opcodes.S_MESSAGE,1,0,msg,false),
     SC_ChatPacket.sharedWire(Opcodes.S_MESSAGE,1,0,msg,false));
  eq(chat(Opcodes.S_SAY,0,n,msg,true),
     SC_ChatPacket.sharedWire(Opcodes.S_SAY,0,n,msg,true));
  eq(chat(Opcodes.S_SAY,15,n,msg,true),
     SC_ChatPacket.sharedWire(Opcodes.S_SAY,15,n,msg,true));
  byte[] a=("킬러-"+n).getBytes(MS949),b=("피해자-"+n).getBytes(MS949);
  eq(pk(a,b),SC_ShowCmd.sharedPkWire(a,b));
 }
 public static void main(String[]args){
  for(String s:new String[]{"","홀짝","오림 안내","NPC 대사","\\f2토너먼트","한글 English"})
    test(1,s);
  int x=0x51ceac13;
  for(int i=0;i<8192;i++){x^=x<<13;x^=x>>>17;x^=x<<5;test(x,"전투-"+x);}
  eq(pk(new byte[0],new byte[0]),SC_ShowCmd.sharedPkWire(new byte[0],new byte[0]));
  System.out.println("PHASE51_CHAT_PK_PARITY_OK "+checks);
 }
}'''
def main():
 p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase51"));args=p.parse_args()
 raw=SRC.read_bytes();assert blob(raw)==PIN,"phase50 source pin mismatch"
 with zipfile.ZipFile(SRC) as z:
  assert z.testzip() is None
  files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
 before=files.copy()
 assert sum(n.endswith(".java") for n in files)==1710 and "CTransport.dll" in files
 patch_chat(files)
 patch_gambling(files,GAMBLE,"public void Gambling2(")
 patch_gambling(files,GAMBLE3,"public void Gambling3(")
 patch_orim(files);patch_pk(files)
 changed=sorted(n for n in files if files[n]!=before[n])
 assert changed==TARGETS,(changed,TARGETS)
 assert files["CTransport.dll"]==before["CTransport.dll"]
 args.work.mkdir(parents=True,exist_ok=True)
 for n,data in files.items():
  if n.startswith("src/") and n.endswith(".java"):
   f=args.work/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(data)
 test=args.work/"testsrc/Phase51ChatPkParity.java";test.parent.mkdir(parents=True,exist_ok=True);test.write_text(TEST)
 OUT.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for n,data in sorted(files.items()):z.writestr(n,data)
 with zipfile.ZipFile(OUT) as z:assert z.testzip() is None and set(z.namelist())==set(files)
 report={"phase":51,"input_blob":PIN,"output_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
  "java_sources":1710,"modified_java":changed,
  "shared_chat_broadcast_methods_migrated":3,
  "gambling_chat_fanouts_migrated":2,
  "orim_self_and_member_fanout_migrated":1,
  "kda_plain_and_colored_fanout_paths_migrated":2,
  "total_direct_fanout_paths_migrated":8,
  "orim_duplicate_self_send_order_preserved":True,
  "gambling_self_before_nearby_kept":True,
  "kda_clan_victim_notification_filter_kept":True,
  "world_visible_traversal_and_release_kept":True,
  "native_unchanged":True,"new_workers_queues_classes":0,
  "no_legacy_fallback":True,"windows_runtime_tested":False}
 (args.work/"audit.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
 print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=="__main__":main()
