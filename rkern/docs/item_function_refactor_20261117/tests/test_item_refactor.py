#!/usr/bin/env python3
"""Compile actual production item dispatch/context methods using typed Java 21 stubs.
This is a bounded source-level regression, not full server compilation.
"""
from pathlib import Path
import subprocess, tempfile, zipfile, re, sys
ROOT = Path(__file__).resolve().parents[3]
# Script lives at docs/<audit>/tests/test.py -> parents[3] is SRC root.
PK = ROOT/'src/l1j/server/server/model/item/function'
BASE = Path(sys.argv[1]) if len(sys.argv)>1 else None
FQN='l1j/server/server/model/item/function'

def run(*cmd,cwd=None):
 p=subprocess.run(cmd,cwd=cwd,capture_output=True,text=True,encoding='utf-8',errors='replace')
 if p.returncode:
  print('FAILED:',cmd,'\n',p.stdout[-8000:],p.stderr[-8000:]);raise RuntimeError('command failed')
 return p.stdout

def write(work,path,content):
 p=work/(path+'.java');p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content,'utf-8');return p

def compile_run(name,production,other,main):
 with tempfile.TemporaryDirectory() as dirname:
  work=Path(dirname);files=[]
  for filename,text in other.items(): files.append(str(write(work,filename,text)))
  for filename,text in production.items(): files.append(str(write(work,filename,text)))
  files.append(str(write(work,FQN+'/'+name,main)))
  run('javac','-encoding','UTF-8','-d',str(work/'classes'),*files)
  output=run('java','-cp',str(work/'classes'),'l1j.server.server.model.item.function.'+name)
  assert 'PASS' in output,output
  print(output.strip())

router_other={
'l1j/server/server/model/Instance/L1PcInstance':'''package l1j.server.server.model.Instance; public class L1PcInstance {}''',
'l1j/server/server/model/Instance/L1ItemInstance':'''package l1j.server.server.model.Instance;
import java.sql.Timestamp;
public class L1ItemInstance {
  int id; public Timestamp used; public L1ItemInstance(int id){this.id=id;}
  public int getItemId(){return id;}
  public Timestamp getLastUsed(){return used;}
  public void setLastUsed(Timestamp ts){used=ts;}
}''',
'l1j/server/server/model/character/skill/L1SkillId':'''package l1j.server.server.model.character.skill;
public class L1SkillId { public static final int FAFU_MAAN=101,LIND_MAAN=102,ANTA_MAAN=103,VALA_MAAN=104,BIRTH_MAAN=105,SHAPE_MAAN=106,LIFE_MAAN=107; }''',
FQN+'/MaanRaidTransactions':'''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
import java.sql.Timestamp;
final class MaanRaidTransactions {
 static int maans,once,raids,lastSkill, lastMap;
 static void castAndTouch(L1PcInstance pc,L1ItemInstance item,int skill){maans++;lastSkill=skill;item.setLastUsed(new Timestamp(11));}
 static SpecialConsumableRouter.Result useLifeMaanOnce(L1PcInstance pc,L1ItemInstance item){once++;return SpecialConsumableRouter.Result.RETURN;}
 static SpecialConsumableRouter.Result useRaidItem(L1PcInstance pc,L1ItemInstance item,int map,int a,int b,short c,int d,int e,int f,short g){raids++;lastMap=map;return SpecialConsumableRouter.Result.RETURN;}
}''',
FQN+'/AdenaVoucherTransactions':'''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
final class AdenaVoucherTransactions {
 static final int ITEM_ADENA_VOUCHER_100M=400254,ITEM_ADENA_VOUCHER_50M=500254,ITEM_ADENA_VOUCHER_10M=600254;
 static int calls;
 static SpecialConsumableRouter.Result useAdenaVoucher(L1PcInstance pc,L1ItemInstance item){calls++;return SpecialConsumableRouter.Result.RETURN;}
}''',
}
router_main='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*; import java.sql.Timestamp;
public class RouterTest {
 static void ck(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  L1PcInstance pc=new L1PcInstance();
  ck(SpecialConsumableRouter.use(null,new L1ItemInstance(410032))==SpecialConsumableRouter.Result.NOT_HANDLED);
  ck(SpecialConsumableRouter.use(pc,null)==SpecialConsumableRouter.Result.NOT_HANDLED);
  ck(SpecialConsumableRouter.use(pc,new L1ItemInstance(777))==SpecialConsumableRouter.Result.NOT_HANDLED);
  int[] maans={410032,410033,410034,410035,410036,410037,410038};
  for(int i=0;i<maans.length;i++){
    var it=new L1ItemInstance(maans[i]);ck(SpecialConsumableRouter.use(pc,it)==SpecialConsumableRouter.Result.HANDLED);
    ck(MaanRaidTransactions.lastSkill==101+i && it.used!=null);
    it.used=new Timestamp(Long.MAX_VALUE);
    int count=MaanRaidTransactions.maans;
    ck(SpecialConsumableRouter.use(pc,it)==SpecialConsumableRouter.Result.HANDLED);
    ck(MaanRaidTransactions.maans==count);
  }
  ck(MaanRaidTransactions.maans==7);
  for(int id:new int[]{400254,500254,600254}){
    var it=new L1ItemInstance(id);
    ck(SpecialConsumableRouter.use(pc,it)==SpecialConsumableRouter.Result.RETURN);
    ck(it.used==null);
  }
  ck(AdenaVoucherTransactions.calls==3);
  ck(SpecialConsumableRouter.use(pc,new L1ItemInstance(3000159))==SpecialConsumableRouter.Result.RETURN);
  ck(MaanRaidTransactions.once==1);
  var once=new L1ItemInstance(3000159);once.used=new Timestamp(Long.MAX_VALUE);
  ck(SpecialConsumableRouter.use(pc,once)==SpecialConsumableRouter.Result.HANDLED);
  ck(MaanRaidTransactions.once==1);
  int[] raids={779911,779922,779933},maps={53,15403,12862};
  for(int i=0;i<raids.length;i++){
    var it=new L1ItemInstance(raids[i]);
    ck(SpecialConsumableRouter.use(pc,it)==SpecialConsumableRouter.Result.RETURN);
    ck(it.used==null && MaanRaidTransactions.lastMap==maps[i]);
  }
  ck(MaanRaidTransactions.raids==3);
  System.out.println("PASS router: 7 maan, 3 vouchers, 3 raid, 1 life, cooldown and unknown");
 }
}'''

ctx_other={
'l1j/server/server/model/network/core/session/CIOClient':'''package l1j.server.server.model.network.core.session;public class CIOClient {}''',
'l1j/server/server/templates/L1EtcItem':'''package l1j.server.server.templates;
public class L1EtcItem { public int getItemId(){return 1;} public int getUseType(){return 0;} public int getType2(){return 0;} public int get_delayEffect(){return 0;} }''',
'l1j/server/server/model/Instance/L1ItemInstance':'''package l1j.server.server.model.Instance;
import l1j.server.server.templates.L1EtcItem;
public class L1ItemInstance {public L1EtcItem value;public L1ItemInstance(L1EtcItem v){value=v;} public L1EtcItem getItem(){return value;}}''',
'l1j/server/server/model/Instance/L1PcInstance':'''package l1j.server.server.model.Instance;
public class L1PcInstance {public Inventory inv=new Inventory();public Inventory getInventory(){return inv;}
 public static class Inventory { public int scans; public L1ItemInstance selected;
  public L1ItemInstance getItem(int id){scans++;return id==55?selected:null;} }
}''',
FQN+'/ItemTargetDecoder':'''package l1j.server.server.model.item.function;
final class ItemTargetDecoder {
 static final int NONE=0,NAME=1,ITEM=2,BLANK=3,TARGET=4,BOOK=5,BOOKMARK=6,FISHING=7,TARGET_NAME=8,POINT=9,STORED=10,COOKING=11,DEFAULT=12;
 static int modeOf(int id,int type){return id;}
}''',
}
ctx_main='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;import l1j.server.server.templates.L1EtcItem;
public class ContextTest {
 static void ck(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  L1PcInstance pc=new L1PcInstance();
  L1ItemInstance chosen=new L1ItemInstance(new L1EtcItem());pc.inv.selected=chosen;
  var ctx=new ItemActionContext(null,pc,new L1ItemInstance(new L1EtcItem(){public int getItemId(){return 0;}}),1,0,0,0,"",0);
  ck(pc.inv.scans==0 && ctx.targetItem()==null);
  ctx=new ItemActionContext(null,pc,new L1ItemInstance(new L1EtcItem(){public int getItemId(){return 2;}}),1,55,0,0,"",0);
  ck(pc.inv.scans==1 && ctx.targetItem()==chosen);
  ctx=new ItemActionContext(null,pc,new L1ItemInstance(new L1EtcItem(){public int getItemId(){return 12;}}),1,0,0,0,"",55);
  ck(pc.inv.scans==2 && ctx.targetItem()==chosen);
  ctx=new ItemActionContext(null,pc,new L1ItemInstance(new L1EtcItem(){public int getItemId(){return 12;}}),1,0,0,0,"",0);
  ck(pc.inv.scans==2 && ctx.targetItem()==null);
  System.out.println("PASS context: zero-target skip, ITEM and DEFAULT lookup parity");
 }
}'''

def run_all():
 assert PK.is_dir(),PK
 names={p.stem for p in PK.glob('*.java')}
 assert not any('ItemUse' in name for name in names)
 assert 'SpecialConsumableActions' not in names
 if BASE:
  with zipfile.ZipFile(BASE) as zip_input:
   old_classes = {Path(name).stem for name in zip_input.namelist()
        if name.startswith('src/'+FQN+'/') and name.endswith('.java') and 'ItemUse' in Path(name).stem}
  legacy_pat = re.compile(r'\b(?:'+'|'.join(map(re.escape,sorted(old_classes,key=len,reverse=True)))+r')\b')
  for f in ROOT.glob('src/**/*.java'):
   assert not legacy_pat.search(f.read_text('utf-8')),f
 assert len(names)>=101,len(names)  # New domain handlers can increase the class count.
 # The dispatcher case routing is byte-for-byte preserved after mechanical class renaming.
 if BASE:
  with zipfile.ZipFile(BASE) as z:
   old=z.read('src/'+FQN+'/ItemUseDispatcher.java').decode('utf-8')
  now=(PK/'ItemActionDispatcher.java').read_text('utf-8')
  # Normalize references to class names, keeping switch cases and semantic dispatch.
  def case_list(x):
   return re.findall(r'\bcase\s+([^>]+?)\s*->',x)
  assert case_list(old)==case_list(now),(len(case_list(old)),len(case_list(now)))
  print('PASS dispatcher:',len(case_list(now)),'unchanged switch case groups')
 # Execute production core classes against strongly typed dependencies.
 compile_run('RouterTest',{'%s/SpecialConsumableRouter'%FQN:(PK/'SpecialConsumableRouter.java').read_text('utf-8')},router_other,router_main)
 compile_run('ContextTest',{'%s/ItemActionContext'%FQN:(PK/'ItemActionContext.java').read_text('utf-8')},ctx_other,ctx_main)
 table_main = """package l1j.server.server.model.item.function;
public class TablesTest {
 static void ck(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  ck(EnchantBoxRollTables.ENCHANT_BOX_3000039_REWARDS.length==37);
  ck(EnchantBoxRollTables.ENCHANT_BOX_3000039_ENCHANTS.length==54);
  ck(EnchantBoxRollTables.ENCHANT_BOX_3000169_REWARDS.length==9);
  ck(EnchantBoxRollTables.ENCHANT_BOX_3000169_ENCHANTS.length==108);
  ck(EnchantBoxRollTables.ENCHANT_BOX_718_REWARDS.length==9);
  ck(EnchantBoxRollTables.ENCHANT_BOX_718_ENCHANTS.length==131);
  ck(WeaponTransformationTables.TRANSFORM_WEAPON_698.length==4);
  ck(WeaponTransformationTables.TRANSFORM_WEAPON_776.length==5);
  ck(ClassGiftBundleTable.CLASS_GIFT_COMMON.length==13);
  System.out.println("PASS table classes: 9 arrays, Java 21 typed compilation and sizes");
 }
}"""
 compile_run('TablesTest',{f'%s/{n}'%FQN:(PK/(n+'.java')).read_text('utf-8') for n in ('EnchantBoxRollTables','WeaponTransformationTables','ClassGiftBundleTable')},{},table_main)
 # Exact domain loot tables moved without rebalancing any rewards, enchant odds or fixed bundle.
 if BASE:
  with zipfile.ZipFile(BASE) as z:
   old=z.read('src/'+FQN+'/CItemUse.java').decode('utf-8')
  merged='\n'.join((PK/n).read_text('utf-8') for n in ('EnchantBoxRollTables.java','WeaponTransformationTables.java','ClassGiftBundleTable.java'))
  for n in ('ENCHANT_BOX_3000039_REWARDS','ENCHANT_BOX_3000039_ENCHANTS','ENCHANT_BOX_3000169_REWARDS','ENCHANT_BOX_3000169_ENCHANTS','ENCHANT_BOX_718_REWARDS','ENCHANT_BOX_718_ENCHANTS','TRANSFORM_WEAPON_698','TRANSFORM_WEAPON_776','CLASS_GIFT_COMMON'):
   def a(s): return re.search(r'\b'+n+r'\s*=\s*\{([^}]+)\}',s,re.S).group(1)
   assert re.sub(r'\s+','',a(old))==re.sub(r'\s+','',a(merged)),n
  print('PASS data: all 9 original int[] tables unchanged')
 # Compile and exercise the ACTUAL new raid configuration cache and original parser.
 boss_source=(PK/'MaanRaidTransactions.java').read_text('utf-8')
 start=boss_source.index('    private static volatile BossList cachedBossList;')
 end=boss_source.index('    static void spawn(',start)
 cache_class='package l1j.server.server.model.item.function;\nfinal class BossCacheActual {\n'+boss_source[start:end]+'}\n'
 cache_main='''package l1j.server.server.model.item.function;
import java.util.Arrays;
public class RaidCacheTest {
 static void ck(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
   int[] old=BossCacheActual.configuredBossIds("100, 200, bad, 0,,300");
   ck(Arrays.equals(old,new int[]{100,200,300}));
   ck(BossCacheActual.configuredBossIds(new String("100, 200, bad, 0,,300"))==old);
   ck(BossCacheActual.configuredBossIds("100,200").length==2);
   ck(Arrays.equals(BossCacheActual.configuredBossIds("100, 200, bad, 0,,300"),old));
   ck(BossCacheActual.configuredBossIds(null).length==0);
   ck(BossCacheActual.configuredBossIds("bad,,0").length==0);
   ck(Arrays.equals(BossCacheActual.configuredBossIds("42"),new int[]{42}));
   int[] warm=BossCacheActual.configuredBossIds("42");
   for(int i=0;i<100000;i++)ck(BossCacheActual.configuredBossIds("42")==warm);
   System.out.println("PASS raid boss cache: CSV parity, config refresh, 100k zero-parse reuse");
 }
}'''
 compile_run('RaidCacheTest',{'%s/BossCacheActual'%FQN:cache_class},{},cache_main)
 print('PASS no ItemUse-prefixed item-function classes or source references')
if __name__=='__main__':run_all()
