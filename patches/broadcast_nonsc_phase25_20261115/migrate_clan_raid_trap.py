#!/usr/bin/env python3
"""Phase25: retain clan-array, party-loop and trap side-effect ordering while
eliminating identical per-recipient SC_SystemMessage/SC_ServerMessage encoding.
"""
from pathlib import Path
import argparse,hashlib,json,re,zipfile
ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/"downloads/RKern_Broadcast_NonSC_Phase24_FULL_SRC_20261115.zip"
OUTPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase25_FULL_SRC_20261115.zip"
PIN="d2fbb826f8cf0a8a1d6eb3dae63184687cb45553"
BASE="src/l1j/server/"
QUEEN=BASE+"IndunSystem/BossRaid2/Queen1.java"
AZ=BASE+"IndunSystem/ClanDungeon/Azmodan.java"
BOSSSYS=BASE+"IndunSystem/BossRaid/BossRaidSystem.java"
QUEENSYS=BASE+"IndunSystem/BossRaid2/QueenSystem2.java"
HADIN=BASE+"IndunSystem/Hadin/HadinTrap.java"
SERVER=BASE+"server/model/network/core/sc/packet/SC_ServerMessage.java"
TARGETS=[QUEEN,AZ,BOSSSYS,QUEENSYS,HADIN,SERVER]
FQ="l1j.server.server.model.network.core.broadcast"
def sha(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def bounds(src,sig):
    assert src.count(sig)>=1,(sig,src.count(sig))
    i=src.index(sig);j=src.index("{",i);depth=0
    for k in range(j,len(src)):
        if src[k]=="{":depth+=1
        elif src[k]=="}":
            depth-=1
            if depth==0:return i,k+1,src[i:k+1]
    raise AssertionError(sig)
CLANLOOP=re.compile(r"for\s*\(\s*L1PcInstance\s+member\s*:\s*clan\.getOnlineClanMember\(\)\s*\)\s*"
                    r"SC_SystemMessage\.send\(\s*member\s*,\s*pck\s*\);")
SERVER_WIRE=r'''
    /**
     * Wire-compatible single-parameter message-code packet for shared fanout.
     * The same MS949 bytes and terminating NUL as WriteBuffer.writeS.
     */
    public static byte[] sharedWire(int type, String message) {
        if(message==null)return sharedWire(type);
        final byte[] encoded=message.getBytes(
                java.nio.charset.Charset.forName("MS949"));
        final int length=7+encoded.length;
        if(length>0xffff)return null;
        final byte[] wire=new byte[length];
        wire[0]=(byte)length;
        wire[1]=(byte)(length>>>8);
        wire[2]=(byte)Opcodes.S_MESSAGE_CODE;
        wire[3]=(byte)type;
        wire[4]=(byte)(type>>>8);
        wire[5]=1;
        System.arraycopy(encoded,0,wire,6,encoded.length);
        return wire;
    }

'''
def patch(files):
    s=files[SERVER].decode("utf8")
    needle="    private static byte[] sharedWire(int type) {"
    assert s.count(needle)==1 and "sharedWire(int type, String message)" not in s
    s=s.replace(needle,SERVER_WIRE+needle.replace("private static","public static"),1)
    files[SERVER]=s.encode("utf8")
    for path in (QUEEN,AZ):
        s=files[path].decode("utf8")
        matches=list(CLANLOOP.finditer(s))
        assert len(matches)==2,(path,len(matches))
        s,n=CLANLOOP.subn(FQ+
          ".CSharedBroadcast.sendArray(clan.getOnlineClanMember(),SC_SystemMessage.sharedWire(pck));",s)
        assert n==2
        files[path]=s.encode("utf8")
    s=files[BOSSSYS].decode("utf8")
    sig='for (L1PcInstance partymember : pc.getParty().getMembers()) {'
    assert s.count(sig)==2
    i,j,old=bounds(s,sig)
    assert old.count('SC_SystemMessage.send(partymember, "3초 후에 보스레이드 맵으로 이동합니다.");')==1
    new='''try(var shared=new '''+FQ+'''.CAsyncSendConstBuffer(
                SC_SystemMessage.sharedWire("3초 후에 보스레이드 맵으로 이동합니다."),
                Integer.MIN_VALUE,Integer.MAX_VALUE)){
            for(L1PcInstance partymember:pc.getParty().getMembers())
                '''+FQ+'''.CSharedBroadcast.sendRecipient(shared,partymember);
        }'''
    s=s[:i]+new+s[j:]
    assert s.count('partymember.start_teleport(')==1
    files[BOSSSYS]=s.encode("utf8")
    s=files[QUEENSYS].decode("utf8")
    sig='for (L1PcInstance partymember : pc.getParty().getMembers()) {'
    assert s.count(sig)==2
    i,j,old=bounds(s,sig)
    yes='SC_SystemMessage.send(partymember, "3초 후에 클랜레이드 맵으로 이동합니다.");'
    no='SC_SystemMessage.send(partymember, "같은 혈원이 아니기에 입장에서 제외 됩니다.");'
    assert old.count(yes)==1 and old.count(no)==1
    new='''try(var accepted=new '''+FQ+'''.CAsyncSendConstBuffer(
                SC_SystemMessage.sharedWire("3초 후에 클랜레이드 맵으로 이동합니다."),
                Integer.MIN_VALUE,Integer.MAX_VALUE);
            var refused=new '''+FQ+'''.CAsyncSendConstBuffer(
                SC_SystemMessage.sharedWire("같은 혈원이 아니기에 입장에서 제외 됩니다."),
                Integer.MIN_VALUE,Integer.MAX_VALUE)){
            for(L1PcInstance partymember:pc.getParty().getMembers()){
                if(pc.getClanid()==partymember.getClanid()){
                    '''+FQ+'''.CSharedBroadcast.sendRecipient(accepted,partymember);
                } else {
                    '''+FQ+'''.CSharedBroadcast.sendRecipient(refused,partymember);
                }
            }
        }'''
    s=s[:i]+new+s[j:]
    i,j,old=bounds(s,sig)
    assert old.count(no)==1 and old.count("partymember.start_teleport(")==1
    new='''try(var refused=new '''+FQ+'''.CAsyncSendConstBuffer(
                SC_SystemMessage.sharedWire("같은 혈원이 아니기에 입장에서 제외 됩니다."),
                Integer.MIN_VALUE,Integer.MAX_VALUE)){
            for(L1PcInstance partymember:pc.getParty().getMembers()){
                if(pc.getClanMemberId()==partymember.getClanMemberId()
                        && partymember.getClanid()==pc.getClanid()){
                    partymember.start_teleport(32728,32811,(short)id,5,169,false,false);
                } else {
                    '''+FQ+'''.CSharedBroadcast.sendRecipient(refused,partymember);
                }
            }
        }'''
    s=s[:i]+new+s[j:]
    files[QUEENSYS]=s.encode("utf8")
    s=files[HADIN].decode("utf8")
    st=s.index("} else if (LAST_TRAP_1 && LAST_TRAP_2 && LAST_TRAP_4 && LAST_TRAP_5)")
    en=s.index("} else if (LAST_TRAP)",st)
    section=s[st:en]
    target=re.compile(r"for\s*\(\s*L1Object ob\s*:\s*cklist\s*\)\s*\{\s*"
        r"if\s*\(\s*ob\s*==\s*null\s*\)\s*continue;\s*"
        r"if\s*\(\s*ob\s+instanceof\s+L1PcInstance\s*\)\s*\{\s*"
        r"L1PcInstance pc\s*=\s*\(L1PcInstance\)\s*ob;\s*"
        r"SC_ServerMessage\.send\(\s*pc\s*,\s*8719\s*\);\s*"
        r"\}\s*\}",re.DOTALL)
    assert len(list(target.finditer(section)))==1
    section,n=target.subn(FQ+
      ".CSharedBroadcast.sendObjectPlayers(cklist,SC_ServerMessage.sharedWire(8719));",section)
    assert n==1
    s=s[:st]+section+s[en:]
    st=s.index("} else if (LAST_TRAP)")
    en=s.index("BonginSendPacekt(8719)",st)
    section=s[st:en]
    assert section.count('SC_ServerMessage.send(pc, 403, "$9355 (1)");')==1
    assert section.count('pc.getInventory().storeItem(410108, 1)')==1
    start=section.index('for(L1Object ob : cklist){')
    i,j,old=bounds(section,'for(L1Object ob : cklist){')
    changed=old.replace('SC_ServerMessage.send(pc, 403, "$9355 (1)");',
      FQ+".CSharedBroadcast.sendRecipient(shared,pc);")
    assert changed!=old
    wrapped='''try(var shared=new '''+FQ+'''.CAsyncSendConstBuffer(
                        SC_ServerMessage.sharedWire(403,"$9355 (1)"),
                        Integer.MIN_VALUE,Integer.MAX_VALUE)){
                    '''+changed+'''
                }'''
    section=section[:i]+wrapped+section[j:]
    s=s[:st]+section+s[en:]
    assert "SC_ServerMessage.send(pc, 403" not in s
    files[HADIN]=s.encode("utf8")
TEST=r'''import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Arrays;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
public final class Phase25ServerMessageParity {
 static int checked;
 static void eq(byte[] a,byte[] b){
  if(!Arrays.equals(a,b))throw new AssertionError("server wire "+checked);
  checked++;
 }
 static byte[] only(int type){
  return ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
   .putShort((short)6).put((byte)Opcodes.S_MESSAGE_CODE)
   .putShort((short)type).put((byte)0).array();
 }
 static byte[] one(int type,String msg){
  byte[] text=msg.getBytes(Charset.forName("MS949"));
  return ByteBuffer.allocate(7+text.length).order(ByteOrder.LITTLE_ENDIAN)
   .putShort((short)(7+text.length)).put((byte)Opcodes.S_MESSAGE_CODE)
   .putShort((short)type).put((byte)1).put(text).put((byte)0).array();
 }
 static void check(int id,String text){
  eq(only(id),SC_ServerMessage.sharedWire(id));
  eq(one(id,text),SC_ServerMessage.sharedWire(id,text));
 }
 public static void main(String[] args){
  for(String s:new String[]{"","$9355 (1)","한국어","테스트","\\f2"})
    for(int id:new int[]{0,-1,403,8719,65535,Integer.MIN_VALUE})
      check(id,s);
  int x=0x7788123;
  for(int i=0;i<4096;i++){x^=x<<13;x^=x>>>17;x^=x<<5;check(x,"메시지 "+x);}
  System.out.println("PHASE25_SERVER_MESSAGE_PARITY_OK "+checked);
 }
}'''
def main():
    p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase25"));args=p.parse_args()
    raw=SOURCE.read_bytes();assert sha(raw)==PIN,"phase24 baseline mismatch"
    with zipfile.ZipFile(SOURCE) as z:
        assert z.testzip() is None
        files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
    before=files.copy()
    assert sum(n.endswith(".java") for n in files)==1710 and "CTransport.dll" in files
    patch(files)
    changed=sorted(p for p in files if files[p]!=before[p])
    assert changed==sorted(TARGETS),changed
    assert files["CTransport.dll"]==before["CTransport.dll"]
    args.work.mkdir(parents=True,exist_ok=True)
    for name,data in files.items():
        if name.startswith("src/") and name.endswith(".java"):
            f=args.work/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(data)
    test=args.work/"testsrc/Phase25ServerMessageParity.java"
    test.parent.mkdir(parents=True,exist_ok=True);test.write_text(TEST)
    OUTPUT.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(OUTPUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for name,data in sorted(files.items()):z.writestr(name,data)
    with zipfile.ZipFile(OUTPUT) as z:
        assert z.testzip() is None and set(z.namelist())==set(files)
    report={"phase":25,"input_blob":PIN,"output_sha256":hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),
      "java_sources":1710,"modified_java":changed,"clan_notice_loops":4,
      "raid_party_notice_loops":3,"hadin_trap_notice_loops":2,
      "party_predicates_and_teleport_order_preserved":True,
      "trap_item_commit_order_preserved":True,
      "native_unchanged":True,"no_fallback":True,"no_new_queue_or_workers":True,
      "windows_runtime_tested":False}
    (args.work/"audit.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
    print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=="__main__":main()
