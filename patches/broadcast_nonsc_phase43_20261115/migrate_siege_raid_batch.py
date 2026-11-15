#!/usr/bin/env python3
"""Phase43: five identical per-receiver SEND sites across siege/raid, one
immutable native frame per packet kind. Keep per-PC two-message FIFO, script
sleep and action ordering, conditional map checks and reward side effects.
"""
from pathlib import Path
import argparse,hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/"downloads/RKern_Broadcast_NonSC_Phase42_FULL_SRC_20261115.zip"
OUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase43_FULL_SRC_20261115.zip"
PIN="aa1f519a8d9f4fb1387e11ad671c6cf19fa80b91"
BASE="src/l1j/server/"
CASTLE=BASE+"MJWarSystem/MJCastleWar.java"
WAR=BASE+"server/model/network/core/sc/packet/SC_WarStartMessage.java"
ICON=BASE+"server/model/network/core/sc/packet/SC_IconMessage.java"
PACKET=BASE+"server/model/network/core/sc/packet/SC_PacketBox.java"
RAID=BASE+"MJRaidSystem/MJRaidObject.java"
VAL=BASE+"MJRaidSystem/Objects/MJRaidValakas.java"
TARGETS=sorted([CASTLE,WAR,ICON,PACKET,RAID,VAL])
FQ="l1j.server.server.model.network.core.broadcast"
def blob(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def bounds(s,sig,start=0,reverse_before=None):
 i=s.index(sig,start) if reverse_before is None else s.rfind(sig,0,reverse_before)
 assert i>=0,sig
 j=s.index("{",i);d=0
 for k in range(j,len(s)):
  if s[k]=="{":d+=1
  elif s[k]=="}":
   d-=1
   if not d:return i,k+1,s[i:k+1]
 raise AssertionError("unbalanced "+sig)
WAR_HELPER=r'''
    /** Immutable full S_EVENT 0x4e castle roster. Seven nul-terminated MS949
     * castle names in the snapshot's original order, including null as "".
     */
    public static byte[] sharedWire(String[] clanNames) {
        if(clanNames==null)throw new IllegalArgumentException("clan names");
        final java.nio.charset.Charset ms949=java.nio.charset.Charset.forName("MS949");
        int size=5;
        for(String name:clanNames){
            final int len=name==null?0:name.getBytes(ms949).length;
            size+=len+1;
            if(size>65535)throw new IllegalStateException("castle roster overflow");
        }
        final byte[] wire=new byte[size];
        wire[0]=(byte)size;wire[1]=(byte)(size>>>8);
        wire[2]=(byte)Opcodes.S_EVENT;wire[3]=0x4e;wire[4]=0x07;
        int at=5;
        for(String name:clanNames){
            if(name!=null){
                final byte[] v=name.getBytes(ms949);
                System.arraycopy(v,0,wire,at,v.length);
                at+=v.length;
            }
            at++;
        }
        if(at!=wire.length)throw new IllegalStateException("roster wire length");
        return wire;
    }

'''
ICON_HELPER=r'''
    /** The existing index-mode S_EXTENDED_PROTOBUF 0x67 frame.
     * No per-recipient writer needed for a fixed siege notice.
     */
    public static byte[] sharedIndexWire(int index,MJSimpleRgb color,int surface,int duration){
        return sharedWire("",index,color,surface,duration,true);
    }

'''
BUFF_HELPER=r'''
    /** Exact constructor-7 DRAGONRAID_BUFF frame, 13 bytes including LE
     * length, S_EVENT, subcode 179, 01 27 0e, D(seconds), H(0x63ef).
     */
    public static byte[] sharedDragonRaidBuffWire(int seconds){
        return new byte[]{13,0,(byte)Opcodes.S_EVENT,(byte)DRAGONRAID_BUFF,
                1,0x27,0x0e,(byte)seconds,(byte)(seconds>>>8),
                (byte)(seconds>>>16),(byte)(seconds>>>24),
                (byte)0xef,0x63};
    }

'''
def patch(files):
 s=files[WAR].decode("utf8")
 marker="    public static boolean send(L1PcInstance pc, String[] clanNames) {"
 assert s.count(marker)==1 and "public static byte[] sharedWire(String[] clanNames)" not in s
 assert "buffer.writeC(Opcodes.S_EVENT);" in s and "buffer.writeC(0x4E);" in s and "buffer.writeC(0x07);" in s
 assert "for (String name : clanNames) buffer.writeS(name);" in s
 s=s.replace(marker,WAR_HELPER+marker,1)
 files[WAR]=s.encode("utf8")
 s=files[ICON].decode("utf8")
 marker="    public static boolean send(L1PcInstance pc, int index, MJSimpleRgb color, int surface, int duration) {"
 assert s.count(marker)==1 and "public static byte[] sharedIndexWire(" not in s
 assert 'return sendInternal(pc, "", index, color, surface, duration, true);' in s
 s=s.replace(marker,ICON_HELPER+marker,1);files[ICON]=s.encode("utf8")
 s=files[PACKET].decode("utf8")
 marker="    public static byte[] sharedGreenWire(String message) {"
 assert s.count(marker)==1 and "public static byte[] sharedDragonRaidBuffWire(" not in s
 assert "case DRAGONRAID_BUFF:" in s and "buffer.writeC(0x01); buffer.writeC(0x27); buffer.writeC(0x0E);" in s
 assert "buffer.writeD(_val1);" in s and "buffer.writeH(0x63EF);" in s
 s=s.replace(marker,BUFF_HELPER+marker,1);files[PACKET]=s.encode("utf8")
 s=files[CASTLE].decode("utf8")
 a,b,old=bounds(s,"private void selectMode() {")
 for x in ("final String[] names = SC_WarStartMessage.snapshot();",
       "for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {",
       "if (pc != null) {",
       "SC_IconMessage.send(pc, 1510, MJSimpleRgb.red(), 6298, 10);",
       "SC_WarStartMessage.send(pc, names);"):
  assert old.count(x)==1,(x,old.count(x))
 new='''private void selectMode() {
        final String[] names=SC_WarStartMessage.snapshot();
        final byte[] iconWire=SC_IconMessage.sharedIndexWire(
                1510,MJSimpleRgb.red(),6298,10);
        final byte[] rosterWire=SC_WarStartMessage.sharedWire(names);
        try(var iconShared=new '''+FQ+'''.CAsyncSendConstBuffer(
                    iconWire,Integer.MIN_VALUE,Integer.MAX_VALUE);
            var rosterShared=new '''+FQ+'''.CAsyncSendConstBuffer(
                    rosterWire,Integer.MIN_VALUE,Integer.MAX_VALUE)){
            for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
                if(pc!=null){
                    '''+FQ+'''.CSharedBroadcast.sendRecipient(iconShared,pc);
                    '''+FQ+'''.CSharedBroadcast.sendRecipient(rosterShared,pc);
                }
            }
        }
    }'''
 s=s[:a]+new+s[b:];files[CASTLE]=s.encode("utf8")
 s=files[RAID].decode("utf8")
 needle="SC_PacketBox.send(pc, SC_PacketBox.DRAGONRAID_BUFF, (int)(MJRaidLoadManager.MRS_RAID_DELAY / 1000));"
 assert s.count(needle)==1
 pos=s.index(needle)
 a,b,loop=bounds(s,"for(int i=0; i<size; i++){",reverse_before=pos)
 assert pos>=a and pos<b and loop.count(needle)==1,(pos,a,b)
 for x in ("SC_OnlyEffect.send(pc, pc.getId(), 7783);","SC_OnlyEffect.broadcast(pc, pc.getId(), 7783);",
           "pc.setSkillEffect(L1SkillId.DRAGONRAID_BUFF", "Timestamp deleteTime =",
           "pc.getNetConnection().getAccount().setDragonRaid(deleteTime);"):
  assert x in loop,(RAID,x)
 newer=loop.replace(needle,FQ+".CSharedBroadcast.sendRecipient(raidShared,pc);",1)
 wrapped='''try(var raidShared=new '''+FQ+'''.CAsyncSendConstBuffer(
                    SC_PacketBox.sharedDragonRaidBuffWire(
                       (int)(MJRaidLoadManager.MRS_RAID_DELAY/1000)),
                    Integer.MIN_VALUE,Integer.MAX_VALUE)){
            '''+newer+'''
        }'''
 s=s[:a]+wrapped+s[b:];files[RAID]=s.encode("utf8")
 s=files[VAL].decode("utf8")
 a,b,loop=bounds(s,"for(int i=0; i<s_startMessagesValakas.length; i++){")
 olda="SC_DisplayEffect.send(pc, s_blackDisplay);"
 oldb="SC_DisplayEffect.send(pc, s_valaksDisplay);"
 assert loop.count(olda)==1 and loop.count(oldb)==1
 for x in ("Thread.sleep(500);","SC_PacketBox.sharedGreenWire(s_startMessagesValakas[i])",
           "pc.getMapId() == mid","createDustRain()"):
  assert x in loop,(VAL,x)
 newer=loop.replace(olda,FQ+".CSharedBroadcast.sendRecipient(blackShared,pc);",1)
 newer=newer.replace(oldb,FQ+".CSharedBroadcast.sendRecipient(valakasShared,pc);",1)
 wrapped='''try(var blackShared=new '''+FQ+'''.CAsyncSendConstBuffer(
                    SC_DisplayEffect.sharedWire(s_blackDisplay),
                    Integer.MIN_VALUE,Integer.MAX_VALUE);
            var valakasShared=new '''+FQ+'''.CAsyncSendConstBuffer(
                    SC_DisplayEffect.sharedWire(s_valaksDisplay),
                    Integer.MIN_VALUE,Integer.MAX_VALUE)){
            '''+newer+'''
        }'''
 s=s[:a]+wrapped+s[b:];files[VAL]=s.encode("utf8")
TEST=r'''import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import l1j.server.server.Opcodes;
import l1j.server.MJTemplate.MJSimpleRgb;
import l1j.server.server.model.network.core.sc.packet.*;
public final class Phase43WarRaidParity {
 static int checks;static final Charset MS949=Charset.forName("MS949");
 static void eq(byte[] a,byte[] b){if(!Arrays.equals(a,b))throw new AssertionError("wire "+checks);checks++;}
 static byte[] raid(int seconds){
   return ByteBuffer.allocate(13).order(ByteOrder.LITTLE_ENDIAN)
      .putShort((short)13).put((byte)Opcodes.S_EVENT)
      .put((byte)SC_PacketBox.DRAGONRAID_BUFF)
      .put((byte)1).put((byte)0x27).put((byte)0x0e)
      .putInt(seconds).putShort((short)0x63ef).array();
 }
 static byte[] roster(String[] values){
   ByteArrayOutputStream out=new ByteArrayOutputStream(160);
   out.write(0);out.write(0);out.write(Opcodes.S_EVENT);
   out.write(0x4e);out.write(7);
   for(String value:values){
     byte[] text=(value==null?"":value).getBytes(MS949);
     out.write(text,0,text.length);out.write(0);
   }
   byte[] raw=out.toByteArray();raw[0]=(byte)raw.length;raw[1]=(byte)(raw.length>>>8);return raw;
 }
 static void var(ByteArrayOutputStream w,long x){
   if(x<0)x&=0xffffffffL;
   while((x &~0x7fL)!=0){w.write((int)((x&127)|128));x>>>=7;}
   w.write((int)x);
 }
 static byte[] icon(int index,MJSimpleRgb color,int surface,int duration){
   ByteArrayOutputStream w=new ByteArrayOutputStream(80);
   w.write(0);w.write(0);w.write(Opcodes.S_EXTENDED_PROTOBUF);
   w.write(0x67);w.write(0);
   w.write(0x08);var(w,surface*2);
   w.write(0x10);var(w,index*2);
   w.write(0x1a);
   byte[] rgb=color.get_bytes();w.write(rgb,0,rgb.length);
   w.write(0x20);var(w,duration);
   w.write(0);w.write(0);
   byte[] b=w.toByteArray();b[0]=(byte)b.length;b[1]=(byte)(b.length>>>8);return b;
 }
 static void check(int x) {
   eq(raid(x),SC_PacketBox.sharedDragonRaidBuffWire(x));
   MJSimpleRgb red=MJSimpleRgb.red();
   eq(icon(x,red,6298,10),SC_IconMessage.sharedIndexWire(x,red,6298,10));
 }
 public static void main(String[] args){
   for(int x:new int[]{0,1,1800,1510,-1,Integer.MIN_VALUE,Integer.MAX_VALUE,65535})check(x);
   String[] names={"켄트성","오크 요새","윈다우드성","기란성","하이네성","지저성","아덴성"};
   eq(roster(names),SC_WarStartMessage.sharedWire(names));
   String[] mixed={"한글",null,"","TEST","장군의 성","한글과 English","\\f2hello"};
   eq(roster(mixed),SC_WarStartMessage.sharedWire(mixed));
   int x=0x45c10d7;
   for(int i=0;i<4096;i++){x^=x<<13;x^=x>>>17;x^=x<<5;check(x);}
   System.out.println("PHASE43_SIEGE_RAID_PARITY_OK "+checks);
 }
}'''
def main():
 p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase43"));a=p.parse_args()
 raw=SRC.read_bytes();assert blob(raw)==PIN,"phase42 full SRC baseline mismatch"
 with zipfile.ZipFile(SRC) as z:
  assert z.testzip() is None
  files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
 old=files.copy()
 assert sum(n.endswith(".java") for n in files)==1710 and "CTransport.dll" in files
 patch(files)
 changed=sorted(n for n in files if files[n]!=old[n]);assert changed==TARGETS,changed
 assert files["CTransport.dll"]==old["CTransport.dll"]
 a.work.mkdir(parents=True,exist_ok=True)
 for name,content in files.items():
  if name.startswith("src/") and name.endswith(".java"):
   f=a.work/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(content)
 t=a.work/"testsrc/Phase43WarRaidParity.java";t.parent.mkdir(parents=True,exist_ok=True);t.write_text(TEST)
 OUT.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for name,content in sorted(files.items()):z.writestr(name,content)
 with zipfile.ZipFile(OUT) as z:assert z.testzip() is None and set(z.namelist())==set(files)
 report={"phase":43,"input_blob":PIN,"output_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
  "java_sources":1710,"modified_java":changed,"individual_packet_send_sites_migrated":5,
  "castle_icon_roster_fifo_order_preserved":True,"dragon_raid_exp_and_buff_sequence_preserved":True,
  "valakas_interleaved_fx_and_sleep_order_preserved":True,
  "native_unchanged":True,"no_fallback":True,"new_workers_queues_classes":0,
  "windows_runtime_tested":False}
 (a.work/"audit.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
 print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=="__main__":main()
