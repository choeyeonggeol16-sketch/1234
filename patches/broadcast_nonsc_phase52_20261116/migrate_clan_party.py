#!/usr/bin/env python3
"""Phase52: clan gaze/list and party name-color fanouts share one complete
plaintext packet each. Preserve live member iteration and per-member FIFO;
list data is snapshotted while holding the SAME source monitor as direct send.
No new threads, queues, classes or legacy broadcast fallback.
"""
from pathlib import Path
import argparse,hashlib,json,re,zipfile
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/"downloads/RKern_Broadcast_NonSC_Phase51_FULL_SRC_20261116.zip"
OUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase52_FULL_SRC_20261116.zip"
PIN="c9789cc88ab6b14494de2c77939b108191b3ecec"
BASE="src/l1j/server/"
ATT=BASE+"server/model/network/core/sc/packet/SC_ClanAttention.java"
ATTR=BASE+"server/model/network/core/cs/packet/CS_Attr.java"
WATCH=BASE+"server/model/network/core/cs/packet/CS_PledgeWatch.java"
PARTY_PACKET=BASE+"server/model/network/core/sc/packet/SC_Party.java"
PARTY=BASE+"server/model/L1Party.java"
TARGETS=sorted([ATT,ATTR,WATCH,PARTY_PACKET,PARTY])
BC="l1j.server.server.model.network.core.broadcast"
def blob(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def block(s,sig,start=0):
 i=s.find(sig,start)
 assert i>=0,(sig,start)
 j=s.index("{",i);depth=0
 for k in range(j,len(s)):
  if s[k]=="{":depth+=1
  elif s[k]=="}":
   depth-=1
   if depth==0:return i,k+1,s[i:k+1]
 raise AssertionError(("unbalanced",sig))
ATT_HELPERS=r'''
    /** Exact S_PLEDGE_WATCH, C(on?32:31), H(269), MS949-S(name).
     * The full LE-framed ciphertext-independent packet is generated once.
     */
    public static byte[] sharedMentWire(boolean on,String name) {
        final byte[] value=(name==null?"":name).getBytes(
                java.nio.charset.Charset.forName("MS949"));
        final long needed=7L+value.length;
        if(needed>0xffffL)return null;
        final int n=(int)needed;
        final byte[] wire=new byte[n];
        wire[0]=(byte)n;
        wire[1]=(byte)(n>>>8);
        wire[2]=(byte)Opcodes.S_PLEDGE_WATCH;
        wire[3]=(byte)(on?32:31);
        wire[4]=(byte)269;
        wire[5]=(byte)(269>>>8);
        System.arraycopy(value,0,wire,6,value.length);
        return wire;
    }

    /** Identical S_PLEDGE_WATCH H(2), D(count), S(name)* frame. The
     * source's original synchronized monitor protects both size and byte
     * snapshot; null entries are skipped exactly like sendList().
     */
    public static byte[] sharedListWire(int count,List<String> source) {
        final java.nio.charset.Charset charset=
                java.nio.charset.Charset.forName("MS949");
        if(source==null){
            final byte[] wire=new byte[9];
            wire[0]=9;
            wire[2]=(byte)Opcodes.S_PLEDGE_WATCH;
            wire[3]=2;
            wire[5]=(byte)count;
            wire[6]=(byte)(count>>>8);
            wire[7]=(byte)(count>>>16);
            wire[8]=(byte)(count>>>24);
            return wire;
        }
        synchronized(source){
            long required=9L;
            for(String name:source) {
                if(name==null)continue;
                required+=name.getBytes(charset).length+1L;
                if(required>0xffffL)return null;
            }
            final int n=(int)required;
            final byte[] wire=new byte[n];
            wire[0]=(byte)n;
            wire[1]=(byte)(n>>>8);
            wire[2]=(byte)Opcodes.S_PLEDGE_WATCH;
            wire[3]=2;
            wire[5]=(byte)count;
            wire[6]=(byte)(count>>>8);
            wire[7]=(byte)(count>>>16);
            wire[8]=(byte)(count>>>24);
            int offset=9;
            for(String name:source){
                if(name==null)continue;
                final byte[] bytes=name.getBytes(charset);
                System.arraycopy(bytes,0,wire,offset,bytes.length);
                offset+=bytes.length+1;
            }
            if(offset!=n)throw new IllegalStateException("gaze list length");
            return wire;
        }
    }

'''
PARTY_HELPER=r'''
    /** Same fixed 10-byte S_EVENT 0x6c D(objectId) H(color) packet. */
    public static byte[] sharedNameColorWire(int objectId,int color) {
        if(color<0 || color>2)return null;
        final byte[] wire=new byte[10];
        wire[0]=10;
        wire[2]=(byte)Opcodes.S_EVENT;
        wire[3]=0x6c;
        wire[4]=(byte)objectId;
        wire[5]=(byte)(objectId>>>8);
        wire[6]=(byte)(objectId>>>16);
        wire[7]=(byte)(objectId>>>24);
        wire[8]=(byte)color;
        wire[9]=(byte)(color>>>8);
        return wire;
    }

'''
def prelude(wires):
 text="try("+ ";\n".join(
    "var "+name+"=new "+BC+".CAsyncSendConstBuffer(\n"+
    wire+",Integer.MIN_VALUE,Integer.MAX_VALUE)" for name,wire in wires)+") {\n"
 return text
def patch_packets(files):
 s=files[ATT].decode("utf8")
 anchor="    public static boolean sendMent(L1PcInstance pc, boolean on, String name) {"
 assert s.count(anchor)==1 and "public static byte[] sharedMentWire(" not in s
 for token in ("buffer.writeC(Opcodes.S_PLEDGE_WATCH);",
               "buffer.writeC(on ? 32 : 31);",
               "buffer.writeH(269);","buffer.writeS(name == null ? \"\" : name);",
               "synchronized (source)"):
  assert token in s,token
 files[ATT]=s.replace(anchor,ATT_HELPERS+anchor,1).encode("utf8")
 s=files[PARTY_PACKET].decode("utf8")
 anchor="    public static boolean sendNameColor(L1PcInstance receiver, L1PcInstance subject, int color) {"
 assert s.count(anchor)==1 and "public static byte[] sharedNameColorWire(" not in s
 for token in ("buffer.writeC(0x6c);","buffer.writeD(subject == null ? 0 : subject.getId());",
               "buffer.writeH(color);"):
  assert token in s,token
 files[PARTY_PACKET]=s.replace(anchor,PARTY_HELPER+anchor,1).encode("utf8")
def patch_attr(files):
 s=files[ATTR].decode("utf8")
 for clan,target in (("pcClan","targetClan"),("targetClan","pcClan")):
  signature="for (L1PcInstance member : "+clan+".getOnlineClanMember()) {"
  a,b,loop=block(s,signature)
  ment="SC_ClanAttention.sendMent(member, true, "+target+".getClanName());"
  roster="SC_ClanAttention.sendList(member, "+clan+".getGazeSize(), "+clan+".getGazeList());"
  assert loop.count(ment)==1 and loop.count(roster)==1,signature
  assert loop.index(ment)<loop.index(roster),signature
  changed=loop.replace(ment,BC+".CSharedBroadcast.sendRecipient(mentShared,member);",1)
  changed=changed.replace(roster,BC+".CSharedBroadcast.sendRecipient(listShared,member);",1)
  opening=prelude([("mentShared","SC_ClanAttention.sharedMentWire(true,"+target+".getClanName())"),
                   ("listShared","SC_ClanAttention.sharedListWire("+clan+".getGazeSize(),"
                   +clan+".getGazeList())")])
  s=s[:a]+opening+changed+"\n}"+s[b:]
 assert "SC_ClanAttention.sendMent(member, true," not in s
 files[ATTR]=s.encode("utf8")
def patch_watch(files):
 s=files[WATCH].decode("utf8")
 for clan in ("clan","targetClan"):
  rx=re.compile(r'for\s*\(\s*L1PcInstance\s+member\s*:\s*'+clan+
                r'\.getOnlineClanMember\(\)\s*\)\s*\{')
  matches=list(rx.finditer(s))
  assert len(matches)==1,(clan,len(matches))
  a,b,loop=block(s,matches[0].group(0),matches[0].start())
  pattern=re.compile(r'SC_ClanAttention\.sendList\s*\(\s*member,\s*'+clan+
                r'\.getGazeSize\(\),\s*'+clan+r'\.getGazeList\(\)\s*\);')
  hits=list(pattern.finditer(loop))
  assert len(hits)==1,(clan,len(hits))
  changed=loop[:hits[0].start()]+BC+".CSharedBroadcast.sendRecipient(shared,member);"+loop[hits[0].end():]
  opening=prelude([("shared","SC_ClanAttention.sharedListWire("+clan+
                             ".getGazeSize(),"+clan+".getGazeList())")])
  s=s[:a]+opening+changed+"\n}"+s[b:]
 files[WATCH]=s.encode("utf8")
def patch_party(files):
 s=files[PARTY].decode("utf8")
 a,b,method=block(s,"public void refresh(L1PcInstance pc) {")
 sig="for (L1PcInstance member : getMembers()) {"
 i,j,loop=block(method,sig)
 old2="SC_Party.sendNameColor(member, pc, 2);"
 old1="SC_Party.sendNameColor(member, pc, 1);"
 assert loop.count(old2)==1 and loop.count(old1)==1
 assert "member == null || pc.getId() == member.getId()" in loop
 assert "leader != null && leader.getId() == pc.getId()" in loop
 changed=loop.replace(old2,BC+".CSharedBroadcast.sendRecipient(shared,member);",1)
 changed=changed.replace(old1,BC+".CSharedBroadcast.sendRecipient(shared,member);",1)
 pref='''final int partyColor=(leader!=null && leader.getId()==pc.getId())?2:1;
        '''
 opening=pref+prelude([("shared","SC_Party.sharedNameColorWire(pc.getId(),partyColor)")])
 method=method[:i]+opening+changed+"\n}"+method[j:]
 s=s[:a]+method+s[b:]
 files[PARTY]=s.encode("utf8")
TEST=r'''import java.util.*;
import java.nio.charset.Charset;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.*;
public final class Phase52ClanPartyParity {
 static int checks;static final Charset MS949=Charset.forName("MS949");
 static void eq(byte[] expected,byte[] actual){
  if(!Arrays.equals(expected,actual))throw new AssertionError("wire "+checks);
  checks++;
 }
 static byte[] ment(boolean on,String name){
  byte[] t=(name==null?"":name).getBytes(MS949);
  ByteBuffer b=ByteBuffer.allocate(7+t.length).order(ByteOrder.LITTLE_ENDIAN);
  b.putShort((short)b.capacity()).put((byte)Opcodes.S_PLEDGE_WATCH)
    .put((byte)(on?32:31)).putShort((short)269).put(t).put((byte)0);
  return b.array();
 }
 static byte[] list(int count,List<String> names) {
  int n=9;
  if(names!=null)for(String s:names)if(s!=null)n+=s.getBytes(MS949).length+1;
  ByteBuffer b=ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN);
  b.putShort((short)n).put((byte)Opcodes.S_PLEDGE_WATCH)
   .putShort((short)2).putInt(count);
  if(names!=null)for(String s:names)if(s!=null)b.put(s.getBytes(MS949)).put((byte)0);
  return b.array();
 }
 static byte[] nameColor(int id,int color){
  return ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
  .putShort((short)10).put((byte)Opcodes.S_EVENT).put((byte)0x6c)
  .putInt(id).putShort((short)color).array();
 }
 static void check(int x,String name){
  eq(ment(true,name),SC_ClanAttention.sharedMentWire(true,name));
  eq(ment(false,name),SC_ClanAttention.sharedMentWire(false,name));
  List<String> names=Arrays.asList("켄트성",name,null,"","혈맹-"+x);
  eq(list(x,names),SC_ClanAttention.sharedListWire(x,names));
  eq(nameColor(x,1),SC_Party.sharedNameColorWire(x,1));
  eq(nameColor(x,2),SC_Party.sharedNameColorWire(x,2));
 }
 public static void main(String[]args){
  eq(list(0,null),SC_ClanAttention.sharedListWire(0,null));
  eq(list(5,Collections.emptyList()),SC_ClanAttention.sharedListWire(5,Collections.emptyList()));
  for(String s:new String[]{"","혈맹 주시","아덴","아이템","Emblem","카오스"})check(10,s);
  int x=0x523196ae;
  for(int i=0;i<4096;i++){x^=x<<13;x^=x>>>17;x^=x<<5;check(x,"주석-"+x);}
  System.out.println("PHASE52_CLAN_PARTY_PARITY_OK "+checks);
 }
}'''
def main():
 p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase52"));args=p.parse_args()
 raw=SRC.read_bytes();assert blob(raw)==PIN,"phase51 source pin mismatch"
 with zipfile.ZipFile(SRC) as z:
  assert z.testzip() is None
  files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
 before=files.copy()
 assert sum(n.endswith(".java") for n in files)==1710 and "CTransport.dll" in files
 patch_packets(files);patch_attr(files);patch_watch(files);patch_party(files)
 changed=sorted(n for n in files if files[n]!=before[n]);assert changed==TARGETS,(changed,TARGETS)
 assert files["CTransport.dll"]==before["CTransport.dll"]
 args.work.mkdir(parents=True,exist_ok=True)
 for n,data in files.items():
  if n.startswith("src/") and n.endswith(".java"):
   f=args.work/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(data)
 test=args.work/"testsrc/Phase52ClanPartyParity.java";test.parent.mkdir(parents=True,exist_ok=True);test.write_text(TEST)
 OUT.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for n,data in sorted(files.items()):z.writestr(n,data)
 with zipfile.ZipFile(OUT) as z:assert z.testzip() is None and set(z.namelist())==set(files)
 report={"phase":52,"input_blob":PIN,"output_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
  "java_sources":1710,"modified_java":changed,
  "clan_alliance_send_sites_migrated":4,
  "pledge_watch_send_sites_migrated":2,
  "party_color_send_sites_migrated":2,
  "total_individual_send_sites_migrated":8,
  "mutual_gaze_update_then_notification_order_kept":True,
  "clan_ment_then_roster_fifo_kept":True,
  "clan_list_snapshot_monitor_preserved":True,
  "party_leader_filter_and_recipient_order_kept":True,
  "native_unchanged":True,"no_fallback":True,"new_workers_queues_classes":0,
  "windows_runtime_tested":False}
 (args.work/"audit.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
 print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=="__main__":main()
