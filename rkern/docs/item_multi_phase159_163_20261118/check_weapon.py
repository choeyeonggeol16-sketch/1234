#!/usr/bin/env python3
"""Java 21 typed compilation of the actual weapon enchant resolution, with failure injection."""
import pathlib, subprocess, tempfile, sys
src=pathlib.Path(sys.argv[1]);prod=src/'l1j/server/server/model/item/function/WeaponEnchantScrollResolution.java'
with tempfile.TemporaryDirectory() as t:
 root=pathlib.Path(t);base='l1j/server/server/model/item/function/'
 def add(p,body):
  x=root/p;x.parent.mkdir(parents=True,exist_ok=True);x.write_text(body,encoding='utf8')
 add('l1j/server/Config.java','''package l1j.server;public final class Config {
 public static int rareweaponLevel=15,weaponLevel=15,Hero_weapon=100,Master_Enchant=100;
 public static boolean ENCHANT_MAX_FAIL=false;
 public static int[] rareweapon=new int[32],weapon=new int[32];
 static {java.util.Arrays.fill(rareweapon,100);java.util.Arrays.fill(weapon,100);}}
 ''')
 add('l1j/server/server/model/Instance/L1ItemInstance.java','''package l1j.server.server.model.Instance;
 public class L1ItemInstance { public int count=1,lv=0;public final Item data=new Item();
 public static class Item {public int id=315,safe=0,material=0,type2=1;
 public int get_safeenchant(){return safe;}public int getItemId(){return id;}
 public int getMaterial(){return material;}public int getType2(){return type2;}}
 public Item getItem(){return data;}public int getEnchantLevel(){return lv;}}
 ''')
 add('l1j/server/server/model/Instance/L1PcInstance.java','''package l1j.server.server.model.Instance;
 public class L1PcInstance {public final Inv inv=new Inv();public boolean _EnchantWeaponSuccess=false;public boolean gm;
 public Inv getInventory(){return inv;}public boolean isGm(){return gm;}
 public static class Inv{public int removals;
 public void removeItem(L1ItemInstance item,int amt){if(item.count<amt)throw new IllegalStateException("empty");removals++;item.count-=amt;}}
 }''')
 add('l1j/server/server/model/item/L1ItemId.java','''package l1j.server.server.model.item;public class L1ItemId {
 public static final int C_SCROLL_OF_ENCHANT_WEAPON=1,ENCHANT_WEAPONA=2;}''')
 add('l1j/server/server/model/network/core/session/CIOClient.java','''package l1j.server.server.model.network.core.session; public class CIOClient{}''')
 for cls in ['SC_ServerMessage','SC_SystemMessage']:
  add('l1j/server/server/model/network/core/sc/packet/'+cls+'.java',f'''package l1j.server.server.model.network.core.sc.packet;
 import l1j.server.server.model.Instance.L1PcInstance;
 public class {cls} {{public static boolean fail;public static int calls;
 public static void send(L1PcInstance p,String s){{calls++;if(fail)throw new RuntimeException("packet-fault");}}
 public static void send(L1PcInstance p,int n,String... args){{calls++;if(fail)throw new RuntimeException("packet-fault");}} }}''')
 add('l1j/server/server/utils/CommonUtil.java','''package l1j.server.server.utils;public class CommonUtil {public static int random(int n){return 0;}}''')
 add(base+'CreatureItemActions.java','''package l1j.server.server.model.item.function;
 public class CreatureItemActions {public static java.util.Random _random=new java.util.Random(0);
 public static final java.util.logging.Logger ITEM_USE2_LOG=java.util.logging.Logger.getLogger("typed-test");}''')
 add(base+'EnchantmentItems.java','''package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;import l1j.server.server.model.network.core.session.CIOClient;
 public class EnchantmentItems {public static int successes,failures;public static L1ItemInstance input;
 public static void SuccessEnchant(L1PcInstance p,L1ItemInstance target,CIOClient c,int d){if(input!=null && input.count!=0)throw new AssertionError("free-enchant");successes++;target.lv+=d;}
 public static void FailureEnchant(L1PcInstance p,L1ItemInstance target,CIOClient c){if(input!=null && input.count!=0)throw new AssertionError("free-destruction");failures++;}
 public static void AttrEnchant(L1PcInstance p,L1ItemInstance s,L1ItemInstance t){} }''')
 add(base+'TransformationItems.java','''package l1j.server.server.model.item.function;import l1j.server.server.model.Instance.*;
 public class TransformationItems {public static int RandomELevel(L1ItemInstance i,int id){return 1;}}''')
 add(base+'AttributeEnchantScrolls.java','''package l1j.server.server.model.item.function;import l1j.server.server.model.Instance.*;
 public class AttributeEnchantScrolls {public static void apply(L1PcInstance p,L1ItemInstance s,L1ItemInstance t){}}''')
 add(base+'WeaponEnchantChanceCalculation.java','''package l1j.server.server.model.item.function;import l1j.server.server.model.Instance.*;
 public class WeaponEnchantChanceCalculation {public static int calculate(L1PcInstance p,L1ItemInstance i,int a,int b,int c){return 100;}}''')
 add(base+'ItemActionContext.java','''package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;import l1j.server.server.model.network.core.session.CIOClient;
 public class ItemActionContext {L1PcInstance p;L1ItemInstance s,t;int id;
 public ItemActionContext(L1PcInstance p,L1ItemInstance s,L1ItemInstance t,int id){this.p=p;this.s=s;this.t=t;this.id=id;}
 public L1PcInstance player(){return p;}public L1ItemInstance item(){return s;}public L1ItemInstance targetItem(){return t;}
 public CIOClient client(){return null;}public int itemId(){return id;}}''')
 add(base+'ItemActionResult.java','''package l1j.server.server.model.item.function;enum ItemActionResult{STOP,COMPLETE}''')
 add(base+'TestHarness.java','''package l1j.server.server.model.item.function;
 import l1j.server.Config;import l1j.server.server.model.Instance.*;
 import l1j.server.server.model.network.core.sc.packet.*;
 class TestHarness {static int n;static void ok(boolean v){n++;if(!v)throw new AssertionError("case "+n);}
 static ItemActionResult call(int scrollId,int weaponId,boolean failPacket){
  L1PcInstance p=new L1PcInstance();L1ItemInstance s=new L1ItemInstance(),t=new L1ItemInstance();t.data.id=weaponId;
  EnchantmentItems.input=s;SC_SystemMessage.fail=failPacket;SC_ServerMessage.fail=failPacket;
  ItemActionResult r=WeaponEnchantScrollResolution.apply(new ItemActionContext(p,s,t,scrollId));
  if(scrollId==68076||scrollId==724||scrollId==810003||scrollId==4100148){ok(s.count==0);ok(p.inv.removals==1);}
  return r;
 }
 public static void main(String[] a){Config.Master_Enchant=100;Config.Hero_weapon=100;
   call(68076,315,true);ok(EnchantmentItems.successes==1);
   call(724,315,true);ok(EnchantmentItems.successes==2);
   call(810003,315,true); // 0 enchant, not eligible, no item consumed; this case below overrides
 }} }''')
 # Execute the compiled production branch with packet failures and exact debit assertions.
 add(base+'TestHarness.java','''package l1j.server.server.model.item.function;
 import l1j.server.Config;import l1j.server.server.model.Instance.*;
 import l1j.server.server.model.network.core.sc.packet.*;
 class TestHarness {static int n;static void ok(boolean v){n++;if(!v)throw new AssertionError("case "+n);}
 static void call(int id,int weaponId,int level,boolean failPacket,boolean expectDebit){
 L1PcInstance p=new L1PcInstance();L1ItemInstance scroll=new L1ItemInstance(),target=new L1ItemInstance();target.data.id=weaponId;target.lv=level;
 EnchantmentItems.input=scroll;SC_SystemMessage.fail=failPacket;SC_ServerMessage.fail=failPacket;
 ItemActionResult result=WeaponEnchantScrollResolution.apply(new ItemActionContext(p,scroll,target,id));
 ok(result==ItemActionResult.COMPLETE);
 ok(scroll.count==(expectDebit?0:1));ok(p.inv.removals==(expectDebit?1:0));
 }
 public static void main(String[] a){Config.Master_Enchant=100;Config.Hero_weapon=100;
 for(int i=0;i<60;i++){
 call(68076,315,1,true,true);
 call(724,315,5,true,true);
 call(810003,315,9,true,true);
 call(4100148,315,8,true,true);
 call(724,999,5,true,false);
 }
 System.out.println("WEAPON_TYPED_CASES="+n+" PASS");
 }
 }''')
 files=list(root.rglob('*.java'))+[prod];out=root/'out';out.mkdir()
 c=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(out),*map(str,files)],capture_output=True,text=True)
 if c.returncode:raise RuntimeError(c.stdout+c.stderr)
 c=subprocess.run(['java','-cp',str(out),'l1j.server.server.model.item.function.TestHarness'],capture_output=True,text=True)
 if c.returncode:raise RuntimeError(c.stdout+c.stderr)
 print(c.stdout.strip())
