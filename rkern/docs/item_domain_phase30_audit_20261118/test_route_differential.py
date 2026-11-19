#!/usr/bin/env python3
"""Compile methods extracted from real Phase21 wrappers against Phase30 switch arms.
Uses typed mock gameplay objects, injects failures before/after inventory effects,
and compares STOP/COMPLETE and action/consume/tail traces.
"""
from pathlib import Path
import re, subprocess, tempfile, hashlib
HERE=Path(__file__).resolve().parent
P21=HERE/'baseline'
P30=HERE.parents[1]/'src/l1j/server/server/model/item/function'

def method_from_file(n,method):
    s=(P21/(n+'.java')).read_text('utf-8')
    x=re.search(r'static\s+(?:ItemActionResult|void)\s+'+method+r'\s*\(',s)
    assert x,(n,method)
    start=x.start(); op=s.index('{',x.end()); level=0
    for i in range(op,len(s)):
        if s[i]=='{':level+=1
        elif s[i]=='}':
            level-=1
            if level==0: return s[start:i+1]
    raise AssertionError(n)

wrappers={'ClassGiftPackageUse':['handleClassPackage'],
          'AdministratorGiftPackageUse':['handleOpenAdminPackage'],
          'ItemCurrencyExchanges':['handleConvertAdena','handleExchangeAdenaVoucher'],
          'TransformationPotionItems':['handlePolyPotion'],
          'CharacterSlotVoucherItems':['use'], 'EquipmentSlotVoucherItems':['use'],
          'RankingRewardItems':['use']}

old_classes=[]
for cls,methods in wrappers.items():
    body='\n'.join(method_from_file(cls,m) for m in methods)
    if cls=='TransformationPotionItems':
        body+='\nstatic void usePolyPotion(L1PcInstance pc,int id){TransformationItems.usePolyPotion(pc,id);}\n'
    old_classes.append('final class '+cls+' {\n'+body+'\n}')

dispatch=(P30/'ItemActionDispatcher.java').read_text('utf-8')
# Exact production switch arms, not reconstructed logic.
arms=[]
for start in ('case 77799 ->','case 123123 ->','case 400253, 875640445 ->',
              'case 400254, 500254, 600254 ->','case 4100284,'):
    a=dispatch.index(start)
    b=dispatch.index('\n',a)
    if start=='case 4100284,':
        b=dispatch.index('\n',dispatch.index('8001, 8002 ->',a))
    line=dispatch[a:b].strip()
    assert 'yield ItemActionResult.' in line,(start,line)
    arms.append(line)

craft=(P30/'SupplementalCraftRouter.java').read_text('utf-8')
reward=(P30/'SupplementalRewardRouter.java').read_text('utf-8')
# Match all six exact equipment IDs and the 5990/210083 branches.
assert re.search(r'case 5990:\s*RankBusiness\.getInstance\(\)\.onExpendiantItem\(pc, l1iteminstance\);\s*break;',craft)
assert re.search(r'case 210083:\s*CharacterSlotReceipt\.useCharacterSlotExpansionAtomic\(client, pc, l1iteminstance\);\s*break;',craft)
assert re.search(r'case 4100203:\s*case 4100140:\s*case 4100141:\s*case 4100142:\s*case 4100143:\s*case 4100144:\s*EquipmentSlotUnlockReceipt\.useSlotUnlockAtomic\(pc, l1iteminstance\);\s*break;',reward)

java=r'''
import java.util.*;
import java.security.*;
import java.nio.charset.*;

enum ItemActionResult { COMPLETE, STOP }
class Log {
 static final StringBuilder s=new StringBuilder();static int flags;
 static void hit(String msg){s.append(msg).append(';');if((flags&1)!=0)throw new IllegalStateException("stage");}
}
class CIOClient {}
class L1ItemInstance {final int id; L1ItemInstance(int id){this.id=id;}}
class L1PcInstance {
 final Inventory inventory=new Inventory();Inventory getInventory(){return inventory;}
 static class Inventory {void removeItem(L1ItemInstance item,int qty){Log.s.append("remove:").append(item.id).append(':').append(qty).append(';');if((Log.flags&2)!=0)throw new IllegalStateException("remove");}}
}
class ItemActionContext {
 final L1PcInstance p;final L1ItemInstance item; final int id;
 ItemActionContext(L1PcInstance p,L1ItemInstance i,int id){this.p=p;item=i;this.id=id;}
 L1PcInstance player(){return p;}L1ItemInstance item(){return item;}int itemId(){return id;}
}
class ClassPackageRewardReceipt {static void useClassPackage(L1PcInstance p,L1ItemInstance i){Log.hit("class"+i.id);}}
class AdminItemPackages {static void open(L1PcInstance p,L1ItemInstance i){Log.hit("admin"+i.id);}}
class AdenaCurrencyConversionReceipt {static void convertAdena(L1PcInstance p,L1ItemInstance i){Log.hit("convert"+i.id);}}
class SpecialConsumableRouter {static void use(L1PcInstance p,L1ItemInstance i){Log.hit("voucher"+i.id);}}
class TransformationItems {static void usePolyPotion(L1PcInstance p,int id){Log.hit("poly"+id);}}
class CharacterSlotReceipt {static void useCharacterSlotExpansionAtomic(CIOClient c,L1PcInstance p,L1ItemInstance i){Log.hit("charSlot"+i.id);}}
class EquipmentSlotUnlockReceipt {static void useSlotUnlockAtomic(L1PcInstance p,L1ItemInstance i){Log.hit("equipSlot"+i.id);}}
class RankBusiness {
 static RankBusiness getInstance(){return new RankBusiness();}
 void onExpendiantItem(L1PcInstance p,L1ItemInstance i){Log.hit("rank"+i.id);}
}
'''+'\n\n'.join(old_classes)+r'''

public class RouteRegression {
 static ItemActionResult baseline(ItemActionContext u) {
  return switch(u.itemId()) {
   case 77799 -> ClassGiftPackageUse.handleClassPackage(u);
   case 123123 -> AdministratorGiftPackageUse.handleOpenAdminPackage(u);
   case 400253,875640445 -> ItemCurrencyExchanges.handleConvertAdena(u);
   case 400254,500254,600254 -> ItemCurrencyExchanges.handleExchangeAdenaVoucher(u);
   case 4100284,4100285,4100286,4100287,4100288,4100289,4200100,4200400,5922901,41143,41144,41145,30057,30058,30059,8000,8001,8002 -> TransformationPotionItems.handlePolyPotion(u);
   default -> ItemActionResult.STOP;
  };
 }
 static ItemActionResult current(ItemActionContext use) {
  L1PcInstance pc=use.player();L1ItemInstance item=use.item();int itemId=use.itemId();
  return switch(itemId) {
'''+'\n'.join(arms)+r'''
   default -> ItemActionResult.STOP;
  };
 }
 static void usePolyPotion(L1PcInstance p,int id){TransformationItems.usePolyPotion(p,id);}
 static void baselineSimple(int id,CIOClient c,L1PcInstance p,L1ItemInstance item,L1ItemInstance target){
   if(id==210083)CharacterSlotVoucherItems.use(c,p,item,id,target);
   else if(id==5990)RankingRewardItems.use(c,p,item,id,target);
   else if(id==4100203||(id>=4100140&&id<=4100144))EquipmentSlotVoucherItems.use(p,item,id,target);
 }
 static void currentSimple(int id,CIOClient c,L1PcInstance p,L1ItemInstance item,L1ItemInstance target){
   switch(id){
    case 210083 -> CharacterSlotReceipt.useCharacterSlotExpansionAtomic(c,p,item);
    case 5990 -> RankBusiness.getInstance().onExpendiantItem(p,item);
    case 4100203,4100140,4100141,4100142,4100143,4100144 -> EquipmentSlotUnlockReceipt.useSlotUnlockAtomic(p,item);
    default -> {}
   }
 }
 static String run(int id,int flags,boolean old) {
  Log.s.setLength(0);Log.flags=flags;
  L1PcInstance pc=new L1PcInstance();L1ItemInstance item=new L1ItemInstance(135+flags);CIOClient c=new CIOClient();ItemActionContext use=new ItemActionContext(pc,item,id);
  try {
   if(id==210083 || id==5990 ||id==4100203 || (id>=4100140&&id<=4100144)){
    if(old)baselineSimple(id,c,pc,item,null);else currentSimple(id,c,pc,item,null);
    return "VOID:"+Log.s;
   }
   ItemActionResult result=old?baseline(use):current(use);
   if(result==ItemActionResult.COMPLETE)Log.s.append("tail;");
   return result+":"+Log.s;
  }catch(RuntimeException err){return "EXCEPTION:"+err.getClass().getSimpleName()+":"+err.getMessage()+":"+Log.s;}
 }
 static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format("%02x",v&255));return s.toString();}
 public static void main(String[] args)throws Exception {
  MessageDigest md=MessageDigest.getInstance("SHA-256");int total=0;
  int[] ids={77799,123123,400253,875640445,400254,500254,600254,
    4100284,4100285,4100286,4100287,4100288,4100289,4200100,4200400,
    5922901,41143,41144,41145,30057,30058,30059,8000,8001,8002,
    210083,5990,4100203,4100140,4100141,4100142,4100143,4100144};
  for(int id:ids)for(int flags=0;flags<64;flags++){
   String a=run(id,flags,true),b=run(id,flags,false);
   if(!a.equals(b))throw new AssertionError(id+" flags="+flags+" old="+a+" new="+b);
   md.update((id+"|"+flags+"|"+a+"\n").getBytes(StandardCharsets.UTF_8));total++;
  }
  System.out.println("PASS phase22-26 extracted production routes: "+total+" scenarios exact match, sha256="+hex(md.digest()));
 }
}
'''
with tempfile.TemporaryDirectory(prefix='r30_routes_') as td:
 p=Path(td);(p/'RouteRegression.java').write_text(java,encoding='utf8')
 a=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',td,str(p/'RouteRegression.java')],capture_output=True,text=True)
 assert a.returncode==0,a.stderr[:12000]
 b=subprocess.run(['java','-cp',td,'RouteRegression'],capture_output=True,text=True)
 assert b.returncode==0,b.stderr[:12000]
 print(b.stdout.strip())
