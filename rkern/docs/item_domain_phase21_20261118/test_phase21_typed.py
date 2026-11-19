#!/usr/bin/env python3
"""JDK 21 original-vs-optimized item receipts, enchant messages and clan-raid parser tests.
This is a reduced typed test harness, not a complete game/server compilation.
"""
from pathlib import Path
import tempfile,subprocess,os,re,hashlib,json
D=Path(__file__).resolve().parent
ROOT=D.parents[1] / 'src/l1j/server/server/model/item/function'
ENV=dict(os.environ,LANG='C.UTF-8',LC_ALL='C.UTF-8')
BASE={}
for nm in ('ClassRewardPackages.java','LevelAndEquipmentPackages.java','EnchantmentSuccessEffects.java','EnchantmentFailureEffects.java','ClanRaidBossSelection.java'):
 BASE[nm]=(D/'baseline'/nm).read_text('utf8')

def run_java(sources,harness,klass='Harness'):
 with tempfile.TemporaryDirectory(prefix='r21typed_') as d:
  path=Path(d)
  for rel,data in sources.items():
   p=path/'src'/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(data,encoding='utf8')
  p=path/'src'/f'{klass}.java';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(harness,encoding='utf8')
  files=[str(p) for p in (path/'src').rglob('*.java')]
  cp=subprocess.run(['javac','-encoding','UTF-8','--release','21','-d',str(path/'classes')]+files,text=True,capture_output=True,env=ENV)
  assert cp.returncode==0,cp.stderr[:12000]
  r=subprocess.run(['java','-cp',str(path/'classes'),klass],text=True,capture_output=True,env=ENV)
  assert r.returncode==0,r.stdout+'\n'+r.stderr[:12000]
  return r.stdout.strip()

def package_test():
 pkg='l1j/server/server/model/item/function/'
 stubs={
  'l1j/server/server/model/Instance/L1ItemInstance.java':'''package l1j.server.server.model.Instance; public class L1ItemInstance {public final int id,itemId;public L1ItemInstance(int id,int iid){this.id=id;itemId=iid;}public int getId(){return id;}public int getItemId(){return itemId;}}''',
  'l1j/server/server/model/Instance/L1PcInstance.java':'''package l1j.server.server.model.Instance; import l1j.server.server.model.item.function.Log;
public class L1PcInstance {public int flags,id=7;public L1ItemInstance item;public Inventory inventory=new Inventory();public int getId(){return id;}public Inventory getInventory(){return inventory;}
public class Inventory {public L1ItemInstance getItem(int id){Log.s.append("get:").append(id).append(';');return (flags&1)==0?item:null;}public boolean checkItem(int id,int count){Log.s.append("check:").append(id).append(',').append(count).append(';');return (flags&2)==0;}}
}''',
  'l1j/server/server/model/L1InventoryTransaction.java':'''package l1j.server.server.model; import l1j.server.server.model.Instance.*;import l1j.server.server.model.item.function.Log;
public class L1InventoryTransaction implements AutoCloseable {private final L1PcInstance pc; private Runnable onFail;
private L1InventoryTransaction(L1PcInstance pc){this.pc=pc;} public static L1InventoryTransaction begin(L1PcInstance pc,String key){Log.s.append("begin:").append(key).append(';'); if((pc.flags&4)!=0)throw new IllegalStateException("begin");return new L1InventoryTransaction(pc);}
public boolean debit(L1ItemInstance item,int c){Log.s.append("debit:").append(item.itemId).append(',').append(c).append(';');return (pc.flags&8)==0;}
public void onFailure(Runnable r){Log.s.append("failcb;");onFail=r;}
public void commit(Runnable r){Log.s.append("commit;");if((pc.flags&32)!=0){if(onFail!=null)onFail.run();return;}r.run();}
public void close(){Log.s.append("close;");}}
''',
  'l1j/server/server/storage/CacheD.java':'package l1j.server.server.storage;public class CacheD {public static String accountOrderKey(String p,int id){return p+id;}}',
  'l1j/server/server/model/network/core/sc/packet/SC_ServerMessage.java':'''package l1j.server.server.model.network.core.sc.packet;import l1j.server.server.model.Instance.*;import l1j.server.server.model.item.function.Log;
public class SC_ServerMessage {public static void send(L1PcInstance pc,int id){Log.s.append("msg:").append(id).append(';');}public static void send(L1PcInstance pc,int id,String s){Log.s.append("msg:").append(id).append(',').append(s).append(';');}}''',
  pkg+'Log.java':'package l1j.server.server.model.item.function; public class Log {public static final StringBuilder s=new StringBuilder();}',
 }
 for name in ('EquipmentStarterPackages','LevelMilestonePackages','StarterSupportPackages','RuneRewardPackages'):
  stubs[pkg+name+'.java']=f'''package l1j.server.server.model.item.function;import java.util.ArrayList;import l1j.server.server.model.L1InventoryTransaction;import l1j.server.server.model.Instance.L1PcInstance;
class {name} {{static void stage(L1PcInstance pc,int id,L1InventoryTransaction tx,ArrayList<String> messages) {{
Log.s.append("stage:{name}:").append(id).append(';');if((pc.flags&16)!=0)throw new IllegalStateException("stage");
if((pc.flags&64)==0){{messages.add("item:"+id);if((pc.flags&128)!=0)messages.add("extra");}}}}
}}
'''
 harness='''import java.util.*;import java.security.*;import java.nio.charset.StandardCharsets;import l1j.server.server.model.Instance.*;import l1j.server.server.model.item.function.*;
public class Harness {
 static byte[] hex(byte[] data){StringBuilder s=new StringBuilder();for(byte b:data)s.append(String.format("%02x",b&255));return s.toString().getBytes(StandardCharsets.US_ASCII);}
 public static void main(String[] args)throws Exception { MessageDigest md=MessageDigest.getInstance("SHA-256");int count=0;
 int[] ids={3000508,7020,7021,4100080,714,715,716,717,721,722,4100164,4100165,3000035,68088,68089,68090,68091,68092,68083,68084,68085,68086,68087,68078,68079,68080,68081,68082,60041,60042,60043,60044,60045,60036,60037,60038,60039,60040,0,1,999999};
 for (int id: ids)for(int f=0;f<256;f++)for(int bad=0;bad<3;bad++){
   L1PcInstance pc=new L1PcInstance();pc.flags=f;L1ItemInstance in=new L1ItemInstance(123,id);pc.item=in;
   if(bad==1)in=new L1ItemInstance(123,id+1);if(bad==2)in=null;
   Log.s.setLength(0);
   try {ENTRY(pc,id,in);}catch(RuntimeException e){Log.s.append("uncaught:").append(e.getClass().getSimpleName()).append(';');}
   md.update((id+"/"+f+"/"+bad+":"+Log.s+"\\n").getBytes(StandardCharsets.UTF_8));count++;
 }
 System.out.println("package_cases="+count+" sha256="+new String(hex(md.digest())));
 }}
'''
 def compile_side(side):
  sources=dict(stubs)
  if side=='before':
   for n in ('ClassRewardPackages.java','LevelAndEquipmentPackages.java'):sources[pkg+n]=BASE[n]
   def entry(id):return 'LevelAndEquipmentPackages.clickItem' if id in {3000508,7020,7021,4100080,714,715,716,717,721,722} else 'ClassRewardPackages.clickItem'
   # ensure unknown ids select previous ClassRewardPackages behavior for fair comparison.
   r='''if(switchId==3000508||switchId==7020||switchId==7021||switchId==4100080||switchId==714||switchId==715||switchId==716||switchId==717||switchId==721||switchId==722)l1j.server.server.model.item.function.LevelAndEquipmentPackages.clickItem(pc,switchId,in);else l1j.server.server.model.item.function.ClassRewardPackages.clickItem(pc,switchId,in);'''
  else:
   sources[pkg+'ItemRewardPackages.java']=(ROOT/'ItemRewardPackages.java').read_text()
   r='l1j.server.server.model.item.function.ItemRewardPackages.clickItem(pc,switchId,in);'
  return run_java(sources,harness.replace('ENTRY(pc,id,in)',f'entry(pc,id,in)').replace('static byte[] hex',f'static void entry(L1PcInstance pc,int switchId,L1ItemInstance in){{{r}}}\n static byte[] hex'))
 out1=compile_side('before');out2=compile_side('after')
 assert out1==out2,(out1,out2)
 print('PASS item receipt typed differential',out1)

# Byte-level confirmation: all 4 stage switch dispatches and receipt flow are kept verbatim.
def source_boundary():
 merged=(ROOT/'ItemRewardPackages.java').read_text()
 original_a=BASE['LevelAndEquipmentPackages.java']; original_b=BASE['ClassRewardPackages.java']
 for src in (original_a,original_b):
  part=src[src.index('if (pc == null'):src.index('switch (itemId)')]
  assert part in merged
  part=src[src.index('            default ->'):src.rfind('}')]
  assert part in merged
  cases=re.search(r'switch \(itemId\) \{(.*?)            default ->',src,re.S).group(1)
  assert cases.strip() in merged
 # Outgoing enchant statements identical after the intended string building improvement.
 for name,method in [('EnchantmentSuccessEffects.java','SuccessEnchant'),('EnchantmentFailureEffects.java','FailureEnchant')]:
  original=BASE[name]
  new=(ROOT/'EnchantmentOutcomeEffects.java').read_text()
  begin=original.index('static void '+method+'(')
  f=original[begin:original.rfind('}')]
  f=re.sub(r'\(new StringBuilder\(\)\)\.append\(pm \+ item\.getEnchantLevel\(\)\)', 'new StringBuilder().append(pm).append(item.getEnchantLevel())', f)
  assert f.strip() in new,method
 print('PASS: original receipt flow and both enchant method bodies preserved (except 10 scoped string optimizations)')

# Compare exact old/new enchant result for all signs, positive/negative levels, and even null names.
def message_test():
 src='''public class Harness {
 public static void main(String[] args){long cases=0;
 for(int enchant=-15;enchant<=30;enchant++)for(String name:new String[]{"test sword","","한글 아이템","+어린이",null})for(String pm:new String[]{"","+"}){
 String before=(new StringBuilder()).append(pm+enchant).append(" ").append(name).toString();
 String after=new StringBuilder().append(pm).append(enchant).append(" ").append(name).toString();
 if(!before.equals(after))throw new AssertionError(before+" != "+after);
 cases++;
 }
 System.out.println("enchant_message_cases="+cases);
 }}'''
 print('PASS',run_java({},src))

# Verify package-level default parse without mutable shared nonempty ids and normal CSV semantics.
def raid_test():
 pkg='l1j/server/server/model/item/function/'
 harness='''package l1j.server.server.model.item.function;import java.util.*;import java.nio.charset.StandardCharsets;import java.security.*;
public class Harness {
static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format("%02x",v&255));return s.toString();}
public static void main(String[] args)throws Exception{
 MessageDigest md=MessageDigest.getInstance("SHA-256");int count=0;
 String[] tests={null,""," "," , , ","0","1","1,2,3","+1, -4, 5","42, abc, 123","0,-1,11","99999999999999999999","1,1,2","  3 \\t, \\n7","123,0,broken,456"};
 for(String s:tests){int[] ids=ClanRaidBossSelection.parseBossIds(s);md.update(Arrays.toString(ids).getBytes(StandardCharsets.UTF_8));count++;}
 System.out.println("raid_cases="+count+" sha256="+hex(md.digest()));
 }}'''
 a=run_java({pkg+'ClanRaidBossSelection.java':BASE['ClanRaidBossSelection.java']},'public class Harness { public static void main(String[] a){} }') if False else None
 # runner lives in same package; transform class name and location
 def run_side(content):
  with tempfile.TemporaryDirectory(prefix='r21raid_') as d:
   d=Path(d); f=d/'l1j/server/server/model/item/function';f.mkdir(parents=True)
   (f/'ClanRaidBossSelection.java').write_text(content)
   (f/'Harness.java').write_text(harness)
   cmd=['javac','-encoding','UTF-8','--release','21','-d',str(d/'classes'),str(f/'ClanRaidBossSelection.java'),str(f/'Harness.java')]
   cp=subprocess.run(cmd,text=True,capture_output=True,env=ENV);assert cp.returncode==0,cp.stderr
   r=subprocess.run(['java','-cp',str(d/'classes'),'l1j.server.server.model.item.function.Harness'],capture_output=True,text=True,env=ENV)
   assert r.returncode==0,r.stderr
   return r.stdout.strip()
 o=run_side(BASE['ClanRaidBossSelection.java']);n=run_side((ROOT/'ClanRaidBossSelection.java').read_text())
 assert o==n,(o,n)
 print('PASS raid CSV original vs merged',o)

# Formatter (Korean/default ROOT locale) vs low-allocation concatenation.
def format_test():
 harness='''import java.util.*;public class Harness{
public static void main(String[] args){Locale.setDefault(Locale.KOREA);int count=0;
String[] formats={"현재 전체 랭킹 %d위 이므로 사용할 수 없습니다.","룸티스보호주문서는 +%d 사용이 가능합니다.","문장은 +%d 이상 보호주문서로 인챈트할 수 없습니다.","%d번 버그베어를 넘어뜨렸습니다."};
int[] nums={-100,-1,0,1,3,5,10,99,10000,Integer.MAX_VALUE};
for(String format:formats)for(int i:nums){int k=format.indexOf("%d");String expect=String.format(format,i);String got=format.substring(0,k)+i+format.substring(k+2);if(!expect.equals(got))throw new AssertionError(expect+" / "+got);count++;}
System.out.println("format_cases="+count);
}}'''
 print('PASS',run_java({},harness))

if __name__=='__main__':
 source_boundary();message_test();package_test();raid_test();format_test()
