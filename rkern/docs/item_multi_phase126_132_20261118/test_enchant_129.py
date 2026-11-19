#!/usr/bin/env python3
"""Compile both production enchant entrypoints plus the exact production
inventory-ownership predicate with Java21 typed doubles; no enchant RNG changes.
"""
from pathlib import Path
import tempfile, subprocess, textwrap, os
root=Path(os.environ['SOURCE_ROOT']) if os.environ.get('SOURCE_ROOT') else Path(__file__).resolve().parents[2]
prod=root/'src/l1j/server/server/model/item/function'
with tempfile.TemporaryDirectory(prefix='rkern129_enchant_') as td:
 base=Path(td)/'src';classes=Path(td)/'classes';classes.mkdir()
 def put(rel,txt):
  f=base/rel;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(textwrap.dedent(txt).strip()+'\n','utf-8')
 for name in ['ArmorEnchantScrollActions','WeaponEnchantScrollActions']:
  put('l1j/server/server/model/item/function/'+name+'.java',(prod/(name+'.java')).read_text('utf-8'))
 text=(prod/'SpecialEnchantScrolls.java').read_text('utf-8')
 start=text.index('public static boolean isOwnedScrollTarget(')
 b=text.index('{',start);end=b+1;depth=1
 while depth:
  if text[end]=='{':depth+=1
  if text[end]=='}':depth-=1
  end+=1
 method=text[start:end]
 put('l1j/server/server/model/item/function/SpecialEnchantScrolls.java','''
 package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;
 public class SpecialEnchantScrolls {'''+method+'}\n')
 put('l1j/server/server/model/Instance/L1ItemInstance.java','''
 package l1j.server.server.model.Instance;
 public class L1ItemInstance {public int id;public Object base=new Object();
 public L1ItemInstance(int x){id=x;}public int getId(){return id;}public Object getItem(){return base;}}
 ''')
 put('l1j/server/server/model/Instance/L1PcInstance.java','''
 package l1j.server.server.model.Instance;
 import java.util.*;
 public class L1PcInstance {public Inventory inv=new Inventory();
 public Inventory getInventory(){return inv;}
 public static final class Inventory {public Map<Integer,L1ItemInstance> items=new HashMap<>();
 public L1ItemInstance getItem(int id){return items.get(id);}}
 }
 ''')
 put('l1j/server/server/model/item/function/ItemActionContext.java','''
 package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;
 public class ItemActionContext {
 public L1PcInstance pc;public L1ItemInstance item,target;
 public L1PcInstance player(){return pc;}public L1ItemInstance item(){return item;}
 public L1ItemInstance targetItem(){return target;}}
 ''')
 put('l1j/server/server/model/item/function/ItemActionResult.java','''
 package l1j.server.server.model.item.function;
 public enum ItemActionResult {STOP,COMPLETE}
 ''')
 for n in ['WeaponEnchantTargetRules','ArmorEnchantTargetRules','WeaponEnchantLevelLimits','ArmorEnchantLevelLimits','WeaponEnchantScrollResolution','ArmorEnchantScrollResolution']:
  put('l1j/server/server/model/item/function/'+n+'.java',f'''
  package l1j.server.server.model.item.function;
  public class {n} {{public static int calls;
   public static ItemActionResult {'apply' if 'Resolution' in n else 'validate'}(ItemActionContext c){{calls++;return ItemActionResult.COMPLETE;}}
  }}
  ''')
 put('l1j/server/server/model/item/function/Test129.java','''
 package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;
 public class Test129 {
 static int cases;
 static void ok(boolean b,String s){cases++;if(!b)throw new AssertionError(s);}
 static ItemActionContext make(){ItemActionContext c=new ItemActionContext();c.pc=new L1PcInstance();
 c.item=new L1ItemInstance(101);c.target=new L1ItemInstance(102);
 c.pc.inv.items.put(101,c.item);c.pc.inv.items.put(102,c.target);return c;}
 static int calls(boolean weapon){return weapon?WeaponEnchantTargetRules.calls:ArmorEnchantTargetRules.calls;}
 static ItemActionResult run(ItemActionContext c,boolean weapon){return weapon?
   WeaponEnchantScrollActions.use(c):ArmorEnchantScrollActions.use(c);}
 public static void main(String[] args){
 for(boolean weapon:new boolean[]{true,false}){
  int old=calls(weapon);ItemActionContext c=make();
  ok(run(c,weapon)==ItemActionResult.COMPLETE,"valid enchant proceeds");
  ok(calls(weapon)==old+1,"valid enchant checks rules");
  old=calls(weapon);c=make();c.target=null;
  ok(run(c,weapon)==ItemActionResult.STOP,"null target rejected");
  ok(calls(weapon)==old,"null target cannot reach rules");
  old=calls(weapon);c=make();c.target=c.item;
  ok(run(c,weapon)==ItemActionResult.STOP,"self target rejected");
  ok(calls(weapon)==old,"self target cannot reach rules");
  old=calls(weapon);c=make();c.pc.inv.items.remove(c.target.id);
  ok(run(c,weapon)==ItemActionResult.STOP,"foreign target rejected");
  ok(calls(weapon)==old,"foreign target cannot reach rules");
  old=calls(weapon);c=make();c.pc.inv.items.remove(c.item.id);
  ok(run(c,weapon)==ItemActionResult.STOP,"stale scroll rejected");
  ok(calls(weapon)==old,"stale scroll cannot reach rules");
  old=calls(weapon);c=make();c.pc.inv.items.put(c.target.id,new L1ItemInstance(c.target.id));
  ok(run(c,weapon)==ItemActionResult.STOP,"same id different instance rejected");
  ok(calls(weapon)==old,"different instance cannot reach rules");
 }
 System.out.println("PHASE129_ENCHANT_OWNERSHIP_ASSERTIONS="+cases);
 }
 }
 ''')
 files=list(base.rglob('*.java'))
 p=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(classes),*map(str,files)],text=True,capture_output=True)
 if p.returncode:print(p.stderr[-10000:]);raise SystemExit(p.returncode)
 p=subprocess.run(['java','-cp',str(classes),'l1j.server.server.model.item.function.Test129'],text=True,capture_output=True)
 print(p.stdout,end='')
 if p.returncode:print(p.stderr[-10000:]);raise SystemExit(p.returncode)
