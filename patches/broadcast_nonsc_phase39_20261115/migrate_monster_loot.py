#!/usr/bin/env python3
"""Phase39: party members' identical monster-loot three-string server notice
is serialized once, and fanned out in original party-array sequence.
Keep item creation, inventory commit/save and non-party sends untouched.
"""
from pathlib import Path
import argparse,hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/"downloads/RKern_Broadcast_NonSC_Phase38_FULL_SRC_20261115.zip"
OUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase39_FULL_SRC_20261115.zip"
PIN="7011812b637609b912eb6aac48175c30fe24f850"
BASE="src/l1j/server/server/"
NPC=BASE+"model/Instance/L1MonsterInstance.java"
PACKET=BASE+"model/network/core/sc/packet/SC_ServerMessage.java"
MODIFIED=sorted([NPC,PACKET])
FQ="l1j.server.server.model.network.core.broadcast"
def blob(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def method(s,sig):
 assert s.count(sig)==1,(sig,s.count(sig))
 a=s.index(sig);b=s.index("{",a);dep=0
 for i in range(b,len(s)):
  if s[i]=="{":dep+=1
  elif s[i]=="}":
   dep-=1
   if dep==0:return a,i+1,s[a:i+1]
 raise AssertionError("unbalanced")
ADDED=r'''
    /** Shared S_MESSAGE_CODE constructor with exactly the three original
     * optional MS949 NUL-terminated arguments and the same argument count.
     */
    public static byte[] sharedWire(int type,String msg1,String msg2,String msg3) {
        final java.nio.charset.Charset charset=
                java.nio.charset.Charset.forName("MS949");
        final byte[] b1=msg1==null?null:msg1.getBytes(charset);
        final byte[] b2=msg2==null?null:msg2.getBytes(charset);
        final byte[] b3=msg3==null?null:msg3.getBytes(charset);
        final long needed=6L+(b1==null?0L:b1.length+1L)
                            +(b2==null?0L:b2.length+1L)
                            +(b3==null?0L:b3.length+1L);
        if(needed>0xffffL)return null;
        final byte[] wire=new byte[(int)needed];
        wire[0]=(byte)needed;
        wire[1]=(byte)(needed>>>8);
        wire[2]=(byte)Opcodes.S_MESSAGE_CODE;
        wire[3]=(byte)type;
        wire[4]=(byte)(type>>>8);
        wire[5]=(byte)((b1==null?0:1)+(b2==null?0:1)+(b3==null?0:1));
        int offset=6;
        if(b1!=null){System.arraycopy(b1,0,wire,offset,b1.length);offset+=b1.length+1;}
        if(b2!=null){System.arraycopy(b2,0,wire,offset,b2.length);offset+=b2.length+1;}
        if(b3!=null){System.arraycopy(b3,0,wire,offset,b3.length);offset+=b3.length+1;}
        if(offset!=wire.length)throw new IllegalStateException("server message size");
        return wire;
    }

'''
def patch(files):
 s=files[PACKET].decode("utf8")
 assert "public static byte[] sharedWire(int type,String msg1,String msg2,String msg3)" not in s
 for token in ("buffer.writeC(Opcodes.S_MESSAGE_CODE);","buffer.writeH(type);",
               "if (msg1 != null) buffer.writeS(msg1);",
               "if (msg2 != null) buffer.writeS(msg2);",
               "if (msg3 != null) buffer.writeS(msg3);"):
  assert token in s,token
 marker="    public static byte[] sharedWire(int type) {"
 assert s.count(marker)==1
 s=s.replace(marker,ADDED+marker,1)
 files[PACKET]=s.encode("utf8")
 s=files[NPC].decode("utf8")
 a,b,old=method(s,"private boolean createNewItem(L1PcInstance pc, int item_id, int count, int Bless) {")
 before='''if (pc.isInParty()) { // 파티 인경우
\t\t\t\tfor (L1PcInstance partymember : pc.getParty().getMembers()) {
\t\t\t\t\tSC_ServerMessage.send(partymember, 813, getNpcTemplate().get_name(), item.getLogName(), pc.getName());
\t\t\t\t}
\t\t\t} else { // 파티가 아닌 경우'''
 assert old.count(before)==1,(old.count(before),"party loop")
 after='''if (pc.isInParty()) { // 파티 인경우
                '''+FQ+'''.CSharedBroadcast.sendArray(
                        pc.getParty().getMembers(),
                        SC_ServerMessage.sharedWire(813,
                                getNpcTemplate().get_name(),item.getLogName(),pc.getName()));
            } else { // 파티가 아닌 경우'''
 old=old.replace(before,after,1)
 for token in ("pc.getInventory().storeItem(item);",
               "pc.getInventory().updateItem(item, L1PcInventory.COL_BLESS);",
               "pc.getInventory().saveItem(item, L1PcInventory.COL_BLESS);",
               "SC_ServerMessage.send(pc, 143, getNpcTemplate().get_name(), item.getLogName());",
               "return true;","return false;"):
  assert token in old,token
 assert "SC_ServerMessage.send(partymember, 813" not in old
 s=s[:a]+old+s[b:]
 files[NPC]=s.encode("utf8")
 return {"party_monster_loot_notice_loops":1,
         "nonparty_loot_packet_unchanged":True,
         "inventory_commit_save_order_unchanged":True,
         "party_array_and_duplicate_order_retained":True,
         "three_nullable_param_wire_count_preserved":True}
TEST=r'''import java.util.Arrays;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
public final class Phase39MonsterLootWireParity {
 static int count;
 static final Charset MS949=Charset.forName("MS949");
 static byte[] expected(int type,String a,String b,String c) {
  byte[][] parts={a==null?null:a.getBytes(MS949),
                  b==null?null:b.getBytes(MS949),
                  c==null?null:c.getBytes(MS949)};
  int size=6,n=0;
  for(byte[] p:parts)if(p!=null){size+=p.length+1;n++;}
  if(size>65535)return null;
  ByteBuffer buffer=ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
  buffer.putShort((short)size).put((byte)Opcodes.S_MESSAGE_CODE)
        .putShort((short)type).put((byte)n);
  for(byte[] p:parts)if(p!=null){buffer.put(p).put((byte)0);}
  return buffer.array();
 }
 static void check(int type,String a,String b,String c){
  byte[] want=expected(type,a,b,c),got=SC_ServerMessage.sharedWire(type,a,b,c);
  if(!Arrays.equals(want,got))throw new AssertionError("loot message parity "+count);
  count++;
 }
 public static void main(String[] args){
  for(String s:new String[]{"","안타라스","아이템 +9","플레이어","ABC","\\f2한글",null}){
   check(813,s,s,s);check(813,s,null,s);check(813,null,s,null);
   check(813,s,null,null);
  }
  int r=0x13371e1a;
  for(int i=0;i<8192;i++){
   r^=r<<13;r^=r>>>17;r^=r<<5;
   String first="보스 "+r, second="획득 "+Integer.toHexString(r),
          third=(i%7==0?null:"파티원"+(i%100));
   check(813,first,second,third);
  }
  check(813,"X".repeat(65535),"Y","Z");
  System.out.println("PHASE39_MONSTER_LOOT_PARITY_OK "+count);
 }
}'''
def main():
 p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase39"));args=p.parse_args()
 raw=SRC.read_bytes();assert blob(raw)==PIN,"phase38 baseline changed"
 with zipfile.ZipFile(SRC) as z:
  assert z.testzip() is None
  files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
 old=files.copy()
 assert sum(n.endswith(".java") for n in files)==1710 and "CTransport.dll" in files
 details=patch(files)
 changed=sorted(n for n in files if files[n]!=old[n])
 assert changed==MODIFIED,changed
 assert files["CTransport.dll"]==old["CTransport.dll"]
 args.work.mkdir(parents=True,exist_ok=True)
 for n,data in files.items():
  if n.startswith("src/") and n.endswith(".java"):
   f=args.work/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(data)
 t=args.work/"testsrc/Phase39MonsterLootWireParity.java";t.parent.mkdir(parents=True,exist_ok=True);t.write_text(TEST)
 OUT.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for n,data in sorted(files.items()):z.writestr(n,data)
 with zipfile.ZipFile(OUT) as z:assert z.testzip() is None and set(z.namelist())==set(files)
 report={"phase":39,"input_blob":PIN,"output_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
    "java_sources":1710,"modified_java":changed,**details,
    "native_unchanged":True,"no_legacy_fallback":True,"new_workers_queues_classes":0,
    "windows_runtime_tested":False}
 (args.work/"audit.json").write_text(json.dumps(report,indent=2,ensure_ascii=False)+"\n")
 print(json.dumps(report,indent=2,ensure_ascii=False))
if __name__=="__main__":main()
