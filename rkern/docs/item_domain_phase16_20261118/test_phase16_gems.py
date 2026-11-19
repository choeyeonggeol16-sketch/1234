#!/usr/bin/env python3
"""Compile and compare actual AinBlessingItems.useGemCase with its relocated production body."""
from pathlib import Path
import tempfile, subprocess,re
R=Path(__file__).resolve().parents[2]
D=R/'docs/item_domain_phase16_20261118'
P=R/'src/l1j/server/server/model/item/function/AinBlessingGemConsumables.java'
base=(D/'AinBlessingItems.java.baseline').read_text('utf-8')
start=base.index('\tpublic static boolean useGemCase(')
end=base.index('\n\tprivate static boolean atMaximum(',start)
original=base[start:end].strip()
assert original.endswith('}')
production=P.read_text('utf-8')
assert original in production,(len(original),original[:100]) # exact relocation
# The real transaction method is intentionally mocked only for comparing decision/after-commit effects.
pkg='l1j/server/server/model/item/function/'
imports='''import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.character.skill.L1SkillId;
import static l1j.server.server.model.character.skill.L1SkillId.DRAGON_PUPLE;
import static l1j.server.server.model.character.skill.L1SkillId.DRAGON_TOPAZ;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsume;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
'''
consume='''public static void consume(L1PcInstance pc,L1ItemInstance item,int delta,int max,Runnable success){
 mock.Trace.log("consume:"+delta+":"+max); success.run();}
'''
orig_src='package l1j.server.server.model.item.function;\n'+imports+'\npublic class AinBlessingItems {\n'+consume+original+'\n}\n'
new_src=production
new_src=re.sub(r'(?m)^import\s+.*?;\n','',new_src)
new_src=new_src.replace('package l1j.server.server.model.item.function;','package l1j.server.server.model.item.function;\n'+imports)
new_src+='\n'
mock_src='package l1j.server.server.model.item.function;\n'+imports+'\npublic class AinBlessingItems {\n'+consume+'}\n'
trace='''package mock;import java.util.*;
public class Trace {public static final ArrayList<String> events=new ArrayList<>();
 public static void log(String x){events.add(x);}}
'''
pc='''package l1j.server.server.model.Instance;
import mock.Trace;
public class L1PcInstance{
 public int level,mask;public int bless;
 public int getLevel(){Trace.log("level");return level;}
 public int getId(){return 777;}
 public Account getAccount(){Trace.log("account");return new Account(bless);}
 public static class Account{int b;Account(int x){b=x;}public int getBlessOfAin(){Trace.log("bless");return b;}}
 public boolean hasSkillEffect(int effect){Trace.log("has:"+effect);return (mask&(1<<effect))!=0;}
 public void removeSkillEffect(int effect){Trace.log("remove:"+effect);}
 public void setSkillEffect(int effect,int duration){Trace.log("set:"+effect+":"+duration);}
 public void sendPackets(String message){Trace.log("packet:"+message);}
}'''
item='''package l1j.server.server.model.Instance;public class L1ItemInstance {}'''
itemid='''package l1j.server.server.model.item;
public class L1ItemId {public static final int DRAGON_DIAMOND2=100001,DRAGON_DIAMOND=100002,
DRAGON_DIAMOND1=100003,DRAGON_SAPPHIRE=100004,DRAGON_RUBY=100005,
EMERALD=100006,EMERALD1=100007;}'''
skillid='''package l1j.server.server.model.character.skill;
public class L1SkillId {public static final int EMERALD_YES=1,EMERALD_NO=2,DRAGON_PUPLE=3,DRAGON_TOPAZ=4;}'''
items='''package l1j.server.server.model.item.function;
import mock.Trace;import l1j.server.server.model.Instance.*;
public class ItemReceiptOperations{public static void UseDurableConsume(L1PcInstance p,L1ItemInstance item,Runnable success){Trace.log("durable");success.run();}}
'''
packets={
'SC_ServerMessage':'''package l1j.server.server.model.network.core.sc.packet;
import mock.Trace;import l1j.server.server.model.Instance.*;
public class SC_ServerMessage{public static void send(L1PcInstance p,int i){Trace.log("server:"+i);}}''',
'SC_SystemMessage':'''package l1j.server.server.model.network.core.sc.packet;
import mock.Trace;import l1j.server.server.model.Instance.*;
public class SC_SystemMessage{public static void send(L1PcInstance p,String s){Trace.log("system:"+s);}}''',
'SC_SkillSound':'''package l1j.server.server.model.network.core.sc.packet;
import mock.Trace;import l1j.server.server.model.Instance.*;
public class SC_SkillSound{public static void send(L1PcInstance p,int id,int s){Trace.log("sound:"+s);}public static void broadcast(L1PcInstance p,int id,int s){Trace.log("soundBroad:"+s);}}''',
'SC_PacketBox':'''package l1j.server.server.model.network.core.sc.packet;
import mock.Trace;import l1j.server.server.model.Instance.*;
public class SC_PacketBox{public static final int EMERALD_ICON=12;
public static void send(L1PcInstance p,int id,int a,int b){Trace.log("box:"+id+":"+a+":"+b);}
public static void send(L1PcInstance p,int id,int a,boolean x,boolean y){Trace.log("boxflags:"+id+":"+a+":"+x+":"+y);}}
'''
}
main='''package l1j.server.server.model.item.function;
import java.security.*;import java.nio.charset.StandardCharsets;import mock.Trace;
import l1j.server.server.model.Instance.*;
public class GemRun {
 public static void main(String[]args)throws Exception{
 int[] ids={410139,100001,100002,100003,1000007,100004,100005,6017,100006,100007,60255,7241,3000457,-1};
 int[] amounts={0,10000,10001,34000000,45000000,47000000,50000000,50000001};
 MessageDigest digest=MessageDigest.getInstance("SHA-256");int n=0;
 for(int item:ids)for(int a:amounts)for(int mask=0;mask<32;mask++)for(int lv:new int[]{81,82}){
  Trace.events.clear();L1PcInstance pc=new L1PcInstance();pc.level=lv;pc.bless=a;pc.mask=mask;
  boolean stop=__CLASS__.useGemCase(pc,new L1ItemInstance(),item);
  digest.update((item+","+a+","+mask+","+lv+","+stop).getBytes(StandardCharsets.UTF_8));
  for(String event:Trace.events){digest.update(event.getBytes(StandardCharsets.UTF_8));digest.update((byte)0);}
  n++;
 }
 System.out.println(n+":"+java.util.HexFormat.of().formatHex(digest.digest()));
 }
}'''

def compiled(impl,clname,entry):
 with tempfile.TemporaryDirectory(prefix='gem-') as td:
  root=Path(td)/'src'
  sources={pkg+clname+'.java':impl,
   pkg+'ItemReceiptOperations.java':items,
   pkg+'GemRun.java':main.replace('__CLASS__',entry),
   'mock/Trace.java':trace,
   'l1j/server/server/model/Instance/L1PcInstance.java':pc,
   'l1j/server/server/model/Instance/L1ItemInstance.java':item,
   'l1j/server/server/model/item/L1ItemId.java':itemid,
   'l1j/server/server/model/character/skill/L1SkillId.java':skillid}
  if clname=='AinBlessingGemConsumables': sources[pkg+'AinBlessingItems.java']=mock_src
  for n,s in packets.items():sources['l1j/server/server/model/network/core/sc/packet/'+n+'.java']=s
  for n,s in sources.items():
   p=root/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(s,'utf-8')
  cls=Path(td)/'classes'
  c=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(cls),*[str(x) for x in root.rglob('*.java')]],capture_output=True,text=True)
  assert c.returncode==0,c.stderr
  e=subprocess.run(['java','-cp',str(cls),'l1j.server.server.model.item.function.GemRun'],capture_output=True,text=True,timeout=60)
  assert e.returncode==0,e.stderr
  return e.stdout.strip()
a=compiled(orig_src,'AinBlessingItems','AinBlessingItems')
b=compiled(new_src,'AinBlessingGemConsumables','AinBlessingGemConsumables')
assert a==b,('original vs refactored',a,b)
print('PASS Phase16 Ain blessing Java21 typed comparison:',a)
