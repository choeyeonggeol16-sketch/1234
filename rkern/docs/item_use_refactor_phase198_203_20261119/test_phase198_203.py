#!/usr/bin/env python3
"""JDK21 typed tests of original-vs-refactored item routing using production methods."""
from pathlib import Path
import re, subprocess, tempfile
P=Path(__file__).resolve().parent
ROOT=P.parents[1]
SRC=ROOT/'src/l1j/server/server/model/item/function'
BASE=P/'baseline'

def cls(path, old, new):
    s=path.read_text()
    # retain actual class implementation, stripping imports and package for stubs
    start=re.search(r'(?m)^(?:public )?final class '+re.escape(old)+r'\s*\{',s)
    assert start, path
    return s[start.start():].replace(old,new)

def go(name, content, expected):
    with tempfile.TemporaryDirectory(prefix='item_route_') as tmp:
        p=Path(tmp)
        (p/'Test.java').write_text(content)
        c=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(p),str(p/'Test.java')],capture_output=True,text=True)
        if c.returncode: raise AssertionError(name+' javac failure: '+c.stderr[:8000])
        r=subprocess.run(['java','-cp',str(p),'Test'],capture_output=True,text=True)
        if r.returncode or expected not in r.stdout:
            raise AssertionError(name+' failed:\n'+r.stdout+'\n'+r.stderr)
        print(name+': '+r.stdout.strip())

# 198 exact ItemActionService bodies, Java-typed caller outcomes (excluding allocation-only event).
old=cls(BASE/'ItemActionService.java','ItemActionService','OldItemActionService')
new=cls(SRC/'ItemActionService.java','ItemActionService','NewItemActionService')
code='''import java.util.logging.*;
class CS_ItemUse {}
class CIOClient {}
class L1PcInstance { boolean selectionStop, admissionStop, etcStop, prepStop, throwHandler; }
class FakeItem {final int id, type2; FakeItem(int a,int b){id=a;type2=b;}int getItemId(){return id;}int getType2(){return type2;}int getUseType(){return 0;} }
class L1ItemInstance {FakeItem item; L1ItemInstance(int id,int type2){item=new FakeItem(id,type2);}FakeItem getItem(){return item;} }
class SupplementalItemIds {static boolean contains(int id){return id==777;} }
class Env {static StringBuilder log = new StringBuilder();static int contexts;static void add(String s){log.append(s).append(';');} }
class SupplementalItemActions {static void use(CIOClient c,L1PcInstance p,L1ItemInstance i,int tid){Env.add("supplemental");}}
class ItemTargetDecoder {static final int ITEM=1,DEFAULT=2,NAME=3,TARGET_NAME=4;static int modeOf(int id,int t){return 0;}static boolean showSelection(L1PcInstance p,int id){Env.add("selection");return !p.selectionStop;} }
class ItemActionAdmission {static boolean canUse(L1PcInstance p,L1ItemInstance i){Env.add("admission");return !p.admissionStop;}static boolean canUseEtc(L1PcInstance p,L1ItemInstance i){Env.add("etc");return !p.etcStop;} }
class ItemActionPreparation {static boolean stopsDispatch(L1PcInstance p,L1ItemInstance i,int id){Env.add("prep");return p.prepStop;} }
class ItemActionContext {L1PcInstance p;L1ItemInstance i;ItemActionContext(CIOClient c,L1PcInstance p,L1ItemInstance i,int oid,int tid,int x,int y,String n,int sid){this.p=p;this.i=i;Env.contexts++;}L1PcInstance player(){return p;} L1ItemInstance item(){return i;} }
enum ItemActionResult {COMPLETE,STOP}
class ItemActionDispatcher {static void useEquipment(L1PcInstance p,L1ItemInstance i,int type2){if(type2==1){Env.add("weapon");}else if(type2==2){Env.add("armor");} if(p.throwHandler)throw new RuntimeException("handler");}static ItemActionResult use(ItemActionContext use){useEquipment(use.p,use.i,use.i.getItem().getType2());return ItemActionResult.COMPLETE;} }
class ItemActionCompletion {static void finish(ItemActionContext use){Env.add("tail");} }
class L1ItemDelay {static void onItemUse(L1PcInstance p,L1ItemInstance i){Env.add("tail");} }
'''+old+'\n'+new+'''
public class Test {
 static String invoke(int variant,L1PcInstance p,L1ItemInstance item){Env.log.setLength(0);Env.contexts=0;try {if(variant==0)OldItemActionService.use(new CIOClient(),p,item,22,23,24,25,"abc",26);else NewItemActionService.use(new CIOClient(),p,item,22,23,24,25,"abc",26);}catch(RuntimeException ex){Env.add("error:"+ex.getMessage());}return Env.log.toString();}
 public static void main(String[] args){int tested=0;int saved=0;for(int type2=0;type2<=4;type2++)for(int control=0;control<128;control++){
 L1PcInstance p=new L1PcInstance();p.selectionStop=(control&1)!=0;p.admissionStop=(control&2)!=0;p.etcStop=(control&4)!=0;p.prepStop=(control&8)!=0;p.throwHandler=(control&16)!=0;int id=(control&32)!=0?777:100;
 L1ItemInstance item=new L1ItemInstance(id,type2);String before=invoke(0,p,item);int oldCount=Env.contexts;String after=invoke(1,p,item);int newCount=Env.contexts;
 if(!before.equals(after))throw new AssertionError("type="+type2+" ctl="+control+" before="+before+" after="+after);
 if(type2!=0 && id!=777 && !p.selectionStop && !p.admissionStop){if(oldCount!=1 || newCount!=0)throw new AssertionError("equipment allocation count");saved++;}
 tested++;}
 System.out.println("PASS "+tested+" outcomes, "+saved+" equipment context allocations avoided");}
}
'''
go('phase198_equipment',code,'PASS 640 outcomes')

# 199 compile both original Java target pipelines and the merged production class.
code='''class ItemActionContext {Object player(){return null;} Object item(){return null;} Object targetItem(){return null;} }
enum ItemActionResult {STOP,COMPLETE}
class Gate {static int bits;static StringBuilder out=new StringBuilder(); static void add(String s){out.append(s).append(';');} static boolean fail(int bit){return (bits&(1<<bit))!=0;} }
class SpecialEnchantScrolls {static boolean isOwnedScrollTarget(Object p,Object i,Object t){Gate.add("owned");return !Gate.fail(0);} }
class WeaponEnchantTargetRules {static ItemActionResult validate(ItemActionContext u){Gate.add("wtarget");return Gate.fail(1)?ItemActionResult.STOP:ItemActionResult.COMPLETE;} }
class ArmorEnchantTargetRules {static ItemActionResult validate(ItemActionContext u){Gate.add("atarget");return Gate.fail(1)?ItemActionResult.STOP:ItemActionResult.COMPLETE;} }
class WeaponEnchantLevelLimits {static ItemActionResult validate(ItemActionContext u){Gate.add("wlevel");return Gate.fail(2)?ItemActionResult.STOP:ItemActionResult.COMPLETE;} }
class ArmorEnchantLevelLimits {static ItemActionResult validate(ItemActionContext u){Gate.add("alevel");return Gate.fail(2)?ItemActionResult.STOP:ItemActionResult.COMPLETE;} }
class WeaponEnchantScrollResolution {static ItemActionResult apply(ItemActionContext u){Gate.add("wapply");return Gate.fail(3)?ItemActionResult.STOP:ItemActionResult.COMPLETE;} }
class ArmorEnchantScrollResolution {static ItemActionResult apply(ItemActionContext u){Gate.add("aapply");return Gate.fail(3)?ItemActionResult.STOP:ItemActionResult.COMPLETE;} }
'''+cls(BASE/'WeaponEnchantScrollActions.java','WeaponEnchantScrollActions','OldWeaponEnchantScrollActions')+'\n'+cls(BASE/'ArmorEnchantScrollActions.java','ArmorEnchantScrollActions','OldArmorEnchantScrollActions')+'\n'+cls(SRC/'EnchantScrollActions.java','EnchantScrollActions','EnchantScrollActions')+'''
public class Test {
 public static void main(String[] args){int count=0; ItemActionContext use=new ItemActionContext();for(int id=0;id<2;id++)for(int bits=0;bits<256;bits++){
 Gate.bits=bits;Gate.out.setLength(0);ItemActionResult old=id==0?OldWeaponEnchantScrollActions.use(use):OldArmorEnchantScrollActions.use(use);String oe=Gate.out.toString();
 Gate.out.setLength(0);ItemActionResult now=id==0?EnchantScrollActions.useWeapon(use):EnchantScrollActions.useArmor(use);String ne=Gate.out.toString();
 if(old!=now||!oe.equals(ne))throw new AssertionError("id="+id+" bits="+bits+" "+old+" "+now+" /"+oe+"/"+ne);count++;}
 System.out.println("PASS "+count+" enchant execution orders & outcomes"); }
}'''
go('phase199_enchant',code,'PASS 512')

# 200 old whole return routing versus new direct route for all switch IDs.
travelOld=cls(BASE/'TravelAndRecoveryItems.java','TravelAndRecoveryItems','OldTravelAndRecoveryItems').replace('ReturnAndTeleportItems.use','OldReturnAndTeleportItems.use')
travelNew=cls(SRC/'TravelAndRecoveryItems.java','TravelAndRecoveryItems','NewTravelAndRecoveryItems')
retOld=cls(BASE/'ReturnAndTeleportItems.java','ReturnAndTeleportItems','OldReturnAndTeleportItems')
route=['IslandReturnScrollUse','ShopTeleportAmuletUse','ClanInnReturnScrollUse','DeployableEffectItems',
       'TravelBuffUseItems','TravelUtilityExchangeItems','ExperienceAndElixirUseItems',
       'CharacterPackageUseItems','BossInvocationScrollItems','AccountPositionRecoveryItems']
code='''class CIOClient {} class L1PcInstance{} class L1ItemInstance{} class InnHelper {static final int INN_KEYID=899998;}
class Env {static StringBuilder out=new StringBuilder();static void log(String s){out.append(s);}}
'''+''.join(f'class {c} {{ static void use(CIOClient c,L1PcInstance p,L1ItemInstance i,int id){{Env.log("{c};");}} }}\n' for c in route)+retOld+'\n'+travelOld+'\n'+travelNew+'''
public class Test {public static void main(String[]args){int count=0;int[] ids={3000147,202099,3000229,875640424,875640415,3000413,240100,748,749,40124,30086,InnHelper.INN_KEYID,700023,41260,731,3000397,3000398,650009,3000128,4100040,60208,60209,60210,700021,60256,700000,4100074,4100075,40033,40034,40035,40036,40037,40038,3000126,3000127,89136,875640417,875640450,123124,12302,3000123,210104,12345};
 for(int id:ids){Env.out.setLength(0);OldTravelAndRecoveryItems.use(null,null,null,id);String old=Env.out.toString();Env.out.setLength(0);NewTravelAndRecoveryItems.use(null,null,null,id);String now=Env.out.toString();if(!old.equals(now))throw new AssertionError("id="+id+" /"+old+"/"+now);count++;}
 System.out.println("PASS "+count+" travel IDs preserve handler routing");}}
'''
go('phase200_travel',code,'PASS 44')

# 201 production dissolution handlers versus new inline switch handlers.
disOld=cls(BASE/'ItemDissolutionScrolls.java','ItemDissolutionScrolls','OldItemDissolutionScrolls')
disNew=(SRC/'ItemActionDispatcher.java').read_text()
start=disNew.index('            case 41245 -> {')
end=disNew.index('            case ',disNew.index('            case 442455 -> {',start)+20)
cases=disNew[start:end]
code='''class L1PcInstance{} class L1ItemInstance{}
class ItemActionContext { L1PcInstance p=new L1PcInstance();L1ItemInstance i=new L1ItemInstance(),t=new L1ItemInstance();L1PcInstance player(){return p;} L1ItemInstance item(){return i;}L1ItemInstance targetItem(){return t;} }
enum ItemActionResult { STOP, COMPLETE }
class Gate {static StringBuilder log=new StringBuilder();}
class OrdinaryResolventItems {static void useResolvent(L1PcInstance p,L1ItemInstance target,L1ItemInstance input){Gate.log.append("normal;");}static void useDragonResolvent(L1PcInstance p,L1ItemInstance target,L1ItemInstance input){Gate.log.append("dragon;");}}
class DragonResolventItems {static void useDragonResolvent(L1PcInstance p,L1ItemInstance target,L1ItemInstance input){Gate.log.append("dragon;");}}
'''+disOld+'''
class NewDissolution {
 static ItemActionResult use(ItemActionContext use,int itemId) {
 L1PcInstance pc=use.player();L1ItemInstance item=use.item();L1ItemInstance targetItem=use.targetItem();
 return switch(itemId) {
'''+cases+'''
 default -> ItemActionResult.COMPLETE;
 }; }
}
public class Test {public static void main(String[]a){int count=0;ItemActionContext u=new ItemActionContext();for(int id:new int[]{41245,442455}){
 Gate.log.setLength(0);ItemActionResult old=id==41245?OldItemDissolutionScrolls.handleResolvent(u):OldItemDissolutionScrolls.handleDragonResolvent(u);String oe=Gate.log.toString();
 Gate.log.setLength(0);ItemActionResult now=NewDissolution.use(u,id);String ne=Gate.log.toString();if(old!=now||!oe.equals(ne))throw new AssertionError("id="+id);count++;}
 System.out.println("PASS "+count+" dissolution outcomes");}}
'''
go('phase201_dissolution',code,'PASS 2')

# Source-scope guard: no references to deleted classes in production SRC.
for name in ('WeaponEnchantScrollActions','ArmorEnchantScrollActions','ReturnAndTeleportItems','ItemDissolutionScrolls'):
    for p in ROOT.glob('src/**/*.java'):
        assert name not in p.read_text(errors='replace'), f'stale deleted class: {name} in {p}'
print('PASS no stale production references')

# 202 exact supplemental router before-vs-after through 18 item IDs.
pre=cls(BASE/'SupplementalCraftRouter.java','SupplementalCraftRouter','OldSupplementalCraftRouter')
post=cls(SRC/'SupplementalCraftRouter.java','SupplementalCraftRouter','NewSupplementalCraftRouter')
hero=cls(BASE/'HeroBundleItems.java','HeroBundleItems','HeroBundleItems')
cons=cls(BASE/'ConsignmentVoucherItems.java','ConsignmentVoucherItems','ConsignmentVoucherItems')
route=['CardFusionChanceItems','CraftingSupportAndTravelItems','UncurseScrollActions',
       'DessertBuffConsumables','TargetedCraftingAndPurificationItems']
code='''class CIOClient {} class L1PcInstance{} class L1ItemInstance{}
class Env {static StringBuilder out=new StringBuilder();static void add(String s){out.append(s).append(';');}}
'''+''.join(f'class {c} {{ static void use(CIOClient c,L1PcInstance p,L1ItemInstance i,int id,L1ItemInstance t){{Env.add("{c}");}} }}\n' for c in route)+'''
class RankBusiness {static RankBusiness getInstance(){return new RankBusiness();}void onExpendiantItem(L1PcInstance p,L1ItemInstance i){Env.add("rank");}}
class HeroPackageReceipt {static void useHeroPackageAtomic(L1PcInstance p,L1ItemInstance i){Env.add("hero");}}
class ConsignmentItemActions {static void register(L1PcInstance p,L1ItemInstance i,L1ItemInstance t){Env.add("consignment");}}
class CharacterSlotReceipt {static void useCharacterSlotExpansionAtomic(CIOClient c,L1PcInstance p,L1ItemInstance i){Env.add("slot");}}
'''+hero+'\n'+cons+'\n'+pre+'\n'+post+'''
public class Test {public static void main(String[]a){int count=0;int[] ids={5200101,5200102,5200103,5990,820018,30106,3000356,3000363,3000364,3000365,410094,210082,753,40097,40119,140119,140329,31118,31119,31120,210083,474747,60035,40925,40926,40927,40928,40929,0,7};
 for(int id:ids){Env.out.setLength(0);OldSupplementalCraftRouter.use(null,null,null,id,null);String old=Env.out.toString();Env.out.setLength(0);NewSupplementalCraftRouter.use(null,null,null,id,null);String now=Env.out.toString();if(!old.equals(now))throw new AssertionError("id="+id+" /"+old+"/"+now);count++;}
 System.out.println("PASS "+count+" supplemental item routes");}}
'''
go('phase202_supplemental',code,'PASS 30')

# 203 actual old dragon-dissolve method compared against the transplanted method.
old=cls(BASE/'DragonResolventItems.java','DragonResolventItems','OldDragonResolventItems')
s=(SRC/'OrdinaryResolventItems.java').read_text()
a=s.index('    static void useDragonResolvent(')
b=s.index('    static void useResolvent(',a)
method=s[a:b]
# strip the tail comment by only using the executable method and its preceding documentation.
code='''class L1PcInstance{}
class L1ItemInstance {int id;L1ItemInstance(int id){this.id=id;}int getItemId(){return id;}}
class Env {static StringBuilder log=new StringBuilder();static boolean valid;static int crystals;static void add(String s){log.append(s).append(';');}}
class ResolventPairRules {static boolean validResolventPair(L1PcInstance p,L1ItemInstance t,L1ItemInstance s,int id){Env.add("valid:"+id);return Env.valid;} }
class ResolventTable {static ResolventTable getInstance(){return new ResolventTable();}int getCrystalCount(int id){Env.add("lookup:"+id);return Env.crystals;} }
class SC_ServerMessage {static void send(L1PcInstance p,int id){Env.add("message:"+id);} }
class ResolventSettlement {static void settleResolvent(L1PcInstance p,L1ItemInstance t,L1ItemInstance s,int item,int count,int bonus,boolean failed){Env.add("settle:"+item+":"+count+":"+bonus+":"+failed);} }
'''+old+'\nclass NewDragonResolventItems {\n'+method+'}\n'+'''
public class Test {public static void main(String[]a){int count=0;L1PcInstance p=new L1PcInstance();L1ItemInstance s=new L1ItemInstance(442455);for(int item:new int[]{0,4000137,4000138,4000140,4000144,4000145,999999})for(int crystals:new int[]{-2,0,1,12,10000})for(int valid=0;valid<=1;valid++){
 Env.valid=valid==1;Env.crystals=crystals;L1ItemInstance target=new L1ItemInstance(item);
 Env.log.setLength(0);OldDragonResolventItems.useDragonResolvent(p,target,s);String before=Env.log.toString();
 Env.log.setLength(0);NewDragonResolventItems.useDragonResolvent(p,target,s);String after=Env.log.toString();
 if(!before.equals(after))throw new AssertionError("item="+item+" count="+crystals+" valid="+valid+" /"+before+"/"+after);count++;}
 System.out.println("PASS "+count+" dragon resolvent paths");}}
'''
go('phase203_dragon_resolvent',code,'PASS 70')

for name in ('HeroBundleItems','ConsignmentVoucherItems','DragonResolventItems'):
    for f in ROOT.glob('src/**/*.java'):
        assert name not in f.read_text(errors='replace'), f'stale deleted class {name} in {f}'
print('PASS all seven removed helper references absent')
