#!/usr/bin/env python3
"""Phase11 byte-identity extraction checks plus Java 21 typed execution regressions."""
from pathlib import Path
import hashlib, json, re, subprocess, tempfile
ROOT=Path(__file__).resolve().parents[2]
FUN=ROOT/'src/l1j/server/server/model/item/function'
D=Path(__file__).resolve().parent
M=json.loads((D/'MANIFEST.json').read_text('utf-8'))
sha=lambda v:hashlib.sha256(v.encode('utf-8')).hexdigest()

def extracted(owner):
    src=(FUN/(owner+'.java')).read_text('utf-8');mark=owner.upper()
    return src.split('        // BEGIN_ORIGINAL_'+mark+'\n',1)[1].split('        // END_ORIGINAL_'+mark,1)[0]

weapon=(D/'WEAPON_PHASE10_BASELINE.txt').read_text('utf-8')
potion=(D/'POTION_PHASE10_BASELINE.txt').read_text('utf-8')
assert sha(weapon)==M['original_weapon_sha256']
assert sha(potion)==M['original_potion_sha256']
assert sha((FUN/'WeaponEnchantScrollActions.java').read_text('utf-8'))==M['gateway_sha256']
assert sha((FUN/'PotionEffects.java').read_text('utf-8'))==M['potion_facade_sha256']
wl=weapon.splitlines(keepends=True)
wblocks={
 'WeaponEnchantTargetRules':''.join(wl[29:163]),
 'WeaponEnchantLevelLimits':''.join(wl[163:180]),
 'WeaponEnchantScrollResolution':''.join(wl[180:444]),
 'WeaponEnchantChanceCalculation':''.join(wl[318:427]),
}
call='''                                int enchant_chance_wepon = WeaponEnchantChanceCalculation.calculate(
                                        pc, targetItem, weaponId, enchant_level, rnd);
'''
for row in M['fragments']:
    owner=row['class'];ref=wblocks[owner];got=extracted(owner)
    if owner=='WeaponEnchantScrollResolution':
        assert got.count(call)==1
        got=got.replace(call,wblocks['WeaponEnchantChanceCalculation'])
    assert got==ref,owner+' source differs from original branch'
    assert sha(ref)==row['sha256'] and len(ref)==row['size'],owner
print('PASS weapon original source identity: 3 regions + extracted chance; all STOP points, RNG call sites unchanged')

pl=potion.splitlines(keepends=True)
expected={
 'HealingPotionConsumables':''.join(pl[100:166]),
 'ExperiencePotionConsumables':''.join(pl[166:177]),
 'MovementPotionConsumables':''.join(pl[177:340]),
 'ManaProtectionPotionConsumables':''.join(pl[340:404]),
}
facade=(FUN/'PotionEffects.java').read_text('utf-8')
assert sha(''.join(pl[410:]))==M['potion_helpers_sha256']
for row in M['potion_sections']:
    cls=row['class'];frag=extracted(cls)
    assert frag==expected[cls] and sha(frag)==row['sha256'] and len(frag)==row['size'],cls
    assert [int(n) for n in re.findall(r'(?m)^\s*case (\d+):',frag)]==row['item_ids']
    for item_id in row['item_ids']:
        assert f'        case {item_id}:\n' in facade
    assert facade.count(f'{cls}.use(pc, l1iteminstance, itemId)')==1
ids=[n for r in M['potion_sections'] for n in r['item_ids']]
assert len(ids)==93 and len(set(ids))==93
assert [int(n) for n in re.findall(r'(?m)^\s*case (\d+):',''.join(pl[100:404]))]==ids
assert facade.count('public static void')==10
print('PASS potion source identity: 93 original item IDs, 4 exact branch bodies, 10 helper signatures retained')

def java_test(files, cls):
    with tempfile.TemporaryDirectory(prefix='rkern_phase11_') as tmp:
        root=Path(tmp)
        for path,content in files.items():
            f=root/path;f.parent.mkdir(parents=True,exist_ok=True)
            f.write_text(content,'utf-8')
        command=['javac','--release','21','-encoding','UTF-8','-d',str(root),*[str(root/p) for p in files]]
        proc=subprocess.run(command,capture_output=True,text=True)
        assert proc.returncode==0,'JAVAC: '+proc.stderr[:11000]
        proc=subprocess.run(['java','-cp',str(root),cls],capture_output=True,text=True)
        assert proc.returncode==0,'JAVA: '+proc.stdout+'\n'+proc.stderr
        print(proc.stdout.strip())

P='l1j/server/server/model/item/function/'
I='l1j/server/server/model/Instance/'
N='l1j/server/server/model/network/core/sc/packet/'
# The actual gateway is compiled with typed stages. Stage STOP must suppress all
# later calls, including cases where a stage has consumed the scroll already.
files={
 P+'WeaponEnchantScrollActions.java':(FUN/'WeaponEnchantScrollActions.java').read_text('utf-8'),
 P+'ItemActionResult.java':(FUN/'ItemActionResult.java').read_text('utf-8'),
 P+'ItemActionContext.java':'package l1j.server.server.model.item.function; final class ItemActionContext {}',
}
for num,cls,func in [(1,'WeaponEnchantTargetRules','validate'),(2,'WeaponEnchantLevelLimits','validate'),(3,'WeaponEnchantScrollResolution','apply')]:
    files[P+cls+'.java']=f'''package l1j.server.server.model.item.function;
final class {cls} {{ static ItemActionResult {func}(ItemActionContext u) {{ Phase11WeaponGate.trace=Phase11WeaponGate.trace*10+{num}; return Phase11WeaponGate.stop=={num}?ItemActionResult.STOP:ItemActionResult.COMPLETE; }} }}'''
files[P+'Phase11WeaponGate.java']='''package l1j.server.server.model.item.function;
public final class Phase11WeaponGate {
 static int stop,trace;
 public static void main(String[] a) {
   for(int n=0;n<=3;n++) {
     stop=n;trace=0;ItemActionResult r=WeaponEnchantScrollActions.use(new ItemActionContext());
     int expected=n==1?1:n==2?12:123;
     if(trace!=expected || r!=(n==0?ItemActionResult.COMPLETE:ItemActionResult.STOP)) throw new AssertionError("stop="+n+" trace="+trace);
   }
   System.out.println("PASS weapon gateway Java 21 STOP short circuit: 4 scenarios");
 }
}'''
java_test(files,'l1j.server.server.model.item.function.Phase11WeaponGate')

# Compare actual production chance code against a separate compilation of the
# exact Phase 10 calculation, including table calls, Config values and GM logs.
rate_src=(FUN/'WeaponEnchantChanceCalculation.java').read_text('utf-8')
props=sorted(set(re.findall(r'Config\.([\w\u0080-\uffff]+)',rate_src)))
assert len(props)>=20,props
files={
 P+'WeaponEnchantChanceCalculation.java':rate_src,
 P+'OriginalWeaponChance.java':'''package l1j.server.server.model.item.function;
import l1j.server.Config;
import l1j.server.server.datatables.WeaponEnchantInformationTable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
final class OriginalWeaponChance {
 static int calculate(L1PcInstance pc,L1ItemInstance targetItem,int weaponId,int enchant_level,int rnd) {
'''+wblocks['WeaponEnchantChanceCalculation']+''' return enchant_chance_wepon; }
}''',
 'l1j/server/Config.java':'package l1j.server; public final class Config { '+ ' '.join(f'public static int {name} = {100+i*19};' for i,name in enumerate(props))+' }',
 'l1j/server/server/datatables/WeaponEnchantInformationTable.java':'''package l1j.server.server.datatables;
import l1j.server.server.model.Instance.L1PcInstance;
public final class WeaponEnchantInformationTable {
 static WeaponEnchantInformationTable inst = new WeaponEnchantInformationTable();
 public static WeaponEnchantInformationTable getInstance(){L1PcInstance.log("gettable"); return inst;}
 public int getChance(int id,int lv){L1PcInstance.log("chance:"+id+":"+lv);return (id%13)+lv*2;}
}''',
 I+'L1PcInstance.java':'''package l1j.server.server.model.Instance;
public final class L1PcInstance {
 public boolean gm;
 public static StringBuilder events = new StringBuilder();
 public static void log(String s){events.append(s).append('|');}
 public boolean isGm(){return gm;}
}''',
 I+'L1ItemInstance.java':'''package l1j.server.server.model.Instance;
public final class L1ItemInstance {
 public int safe;
 public L1ItemInstance getItem(){return this;}
 public int get_safeenchant(){return safe;}
}''',
 N+'SC_SystemMessage.java':'''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public final class SC_SystemMessage { public static void send(L1PcInstance p,String msg){L1PcInstance.log("gm:"+msg);} }''',
 P+'Phase11WeaponChance.java':'''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
public final class Phase11WeaponChance {
 public static void main(String[] a) {
   int count=0;
   for(int safe:new int[]{-1,0,1,6,9}) for(int level=-7;level<=27;level++) for(int gm=0;gm<2;gm++) for(int id:new int[]{7,309,7000213}) {
    L1PcInstance pc=new L1PcInstance();pc.gm=gm==1;
    L1ItemInstance item=new L1ItemInstance();item.safe=safe;
    int rnd=(safe*19+level*17+gm*13+id)&127;
    L1PcInstance.events.setLength(0);
    int expected=OriginalWeaponChance.calculate(pc,item,id,level,rnd);String trace=L1PcInstance.events.toString();
    L1PcInstance.events.setLength(0);
    int actual=WeaponEnchantChanceCalculation.calculate(pc,item,id,level,rnd);
    if(expected!=actual||!trace.equals(L1PcInstance.events.toString()))throw new AssertionError("safe="+safe+" level="+level+" gm="+gm+" id="+id);
    count++;
   }
   System.out.println("PASS actual weapon chance Java 21 equivalence: "+count+" inputs + GM diagnostics & table call trace");
 }
}''',
}
java_test(files,'l1j.server.server.model.item.function.Phase11WeaponChance')

# Compare actual production potion facade and all four production handlers with
# 10th-pass original switch on every known item ID, varying map, player class,
# missing heal potion, and testing both return values and ordered effects.
files={P+'PotionEffects.java':facade}
for r in M['potion_sections']:
    files[P+r['class']+'.java']=(FUN/(r['class']+'.java')).read_text('utf-8')
orig_method=''.join(pl[98:409])
files[P+'OriginalPotionEffects.java']='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import static l1j.server.server.model.item.function.PotionEffects.*;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsume;
public final class OriginalPotionEffects {
'''+orig_method+''' }'''
files[I+'L1ItemInstance.java']='''package l1j.server.server.model.Instance;
public final class L1ItemInstance {public int id;public L1ItemInstance(int id){this.id=id;}}'''
files[I+'L1PcInstance.java']='''package l1j.server.server.model.Instance;
public final class L1PcInstance {
 public static StringBuilder trace = new StringBuilder(); public static boolean missing;
 public static void log(String str){trace.append(str).append('|');}
 public int map,kind;public Inventory inv=new Inventory();
 public int getMapId(){return map;}
 public boolean isKnight(){return kind==1;}
 public boolean isWarrior(){return kind==2;}
 public boolean isCrown(){return kind==3;}
 public boolean isElf(){return kind==4;}
 public boolean isBlackwizard(){return kind==5;}
 public boolean isWizard(){return kind==6;}
 public void setDrink(boolean b){log("drink:"+b);}
 public Inventory getInventory(){return inv;}
 public static final class Inventory {public void removeItem(L1ItemInstance item,int count){log("consume:"+item.id+":"+count);}}
}'''
files[N+'SC_ServerMessage.java']='''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public final class SC_ServerMessage {public static void send(L1PcInstance p,int id){L1PcInstance.log("server:"+id);}}'''
files[N+'SC_SystemMessage.java']='''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public final class SC_SystemMessage {public static void send(L1PcInstance p,String msg){L1PcInstance.log("system:"+msg);}}'''
files[P+'L1HealingPotion.java']='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
public final class L1HealingPotion {
 public static L1HealingPotion get(int id){L1PcInstance.log("healget:"+id);return L1PcInstance.missing?null:new L1HealingPotion();}
 public void use(L1PcInstance p,L1ItemInstance i){L1PcInstance.log("healuse:"+i.id);}
}'''
files[P+'ItemReceiptOperations.java']='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
public final class ItemReceiptOperations {
 public static void UseDurableConsume(L1PcInstance p,L1ItemInstance i,Runnable committed){L1PcInstance.log("receipt:"+i.id);committed.run();}
}'''
for name,methodlist in [
  ('ExperiencePotionEffects',['UseExpPotion1','UseExpPotion']),
  ('MovementPotionEffects',['useGreenPotion','useBravePotion','useFruit']),
  ('ManaPotionEffects',['useBluePotion','useWisdomPotion']),
  ('ProtectivePotionEffects',['useBlessOfEva','useBlindPotion']),
  ('CashScrollPotionEffects',['useCashScroll'])]:
    methods=[]
    for method in methodlist:
        parms=('L1PcInstance p' if method=='useBlindPotion' else 'L1PcInstance p,int id')
        arg=('' if method=='useBlindPotion' else '+":"+id')
        methods.append(f'public static void {method}({parms}) {{L1PcInstance.log("{method}"{arg});}}')
    files[P+name+'.java']='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.L1PcInstance;
public final class '''+name+' {'+' '.join(methods)+'}'
files[P+'Phase11PotionRuntime.java']='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
public final class Phase11PotionRuntime {
 public static void main(String[]args){
   int[] ids=new int[]{'''+','.join(map(str,ids+[0,-1,30057,210116,40017,500000]))+'''};
   int[] maps=new int[]{0,1005,1022,1004,6000,6001,6998,6999,2101,2151,12152,12200,50000};
   int count=0;
   for(int id:ids)for(int map:maps)for(int kind=0;kind<8;kind++)for(int missing=0;missing<2;missing++) {
     L1PcInstance pc=new L1PcInstance();pc.map=map;pc.kind=kind;L1PcInstance.missing=missing==1;
     L1ItemInstance item=new L1ItemInstance(id);
     L1PcInstance.trace.setLength(0);boolean a=OriginalPotionEffects.usePotionCase(pc,item,id);String trace=L1PcInstance.trace.toString();
     L1PcInstance.trace.setLength(0);boolean b=PotionEffects.usePotionCase(pc,item,id);
     if(a!=b||!trace.equals(L1PcInstance.trace.toString()))throw new AssertionError("id="+id+" map="+map+" kind="+kind+" missing="+missing+" expected="+a+"/"+trace+" actual="+b+"/"+L1PcInstance.trace);
     count++;
   }
   System.out.println("PASS original-vs-production potion Java 21 equivalence: "+count+" inputs, 93 IDs + invalid IDs, effects & returns");
 }
}'''
java_test(files,'l1j.server.server.model.item.function.Phase11PotionRuntime')
print('PASS phase11 all tests')
