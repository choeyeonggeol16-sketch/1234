#!/usr/bin/env python3
"""Compile actual ArmorEnchantScrollResolution.java with Java21 typed mocks; verify destructive rune failure transaction admission."""
import sys,tempfile,subprocess
from pathlib import Path
src=Path(sys.argv[1]); prod=src/'l1j/server/server/model/item/function/ArmorEnchantScrollResolution.java'
with tempfile.TemporaryDirectory(prefix='rk_armor_') as tmp:
 r=Path(tmp)
 def add(name,body):
  p=r/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body,encoding='utf8')
 add('l1j/server/Config.java','''package l1j.server;public final class Config {
 public static int armorLevel=15, accessoryLevel=15, accessorytest=15, accessorytest1=15, 할파스갑옷=15;
 public static int[] 할파스갑옷인챈=new int[32];
 public static int[] armor=new int[32],armor1=new int[32],accessory=new int[32],ancient=new int[32],ancient1=new int[32],ancient2=new int[32]; }''')
 add('l1j/server/server/shared/GameRandom.java','''package l1j.server.server.shared; public class GameRandom {public static boolean win=false;public static boolean isWinning(int n,int dice){return win;} }''')
 add('l1j/server/server/datatables/AccessoryEnchantInformationTable.java','''package l1j.server.server.datatables;public class AccessoryEnchantInformationTable {}''')
 add('l1j/server/server/datatables/ArmorEnchantInformationTable.java','''package l1j.server.server.datatables;public class ArmorEnchantInformationTable {}''')
 add('l1j/server/server/templates/L1Armor.java','''package l1j.server.server.templates; public class L1Armor {
 public int id=900116,type2=2,type=4,safe=0;
 public int get_safeenchant(){return safe;}public int getItemId(){return id;}public int getType2(){return type2;}public int getType(){return type;}
 }''')
 add('l1j/server/server/model/Instance/L1ItemInstance.java','''package l1j.server.server.model.Instance;
 import l1j.server.server.templates.L1Armor;
 public class L1ItemInstance {public L1Armor data=new L1Armor();public int lv=0, count=1;
 public L1Armor getItem(){return data;}public int getEnchantLevel(){return lv;}public String getName(){return "rune";}public String getLogName(){return "rune";}
 public int getCount(){return count;}
 }''')
 add('l1j/server/server/model/Instance/L1PcInstance.java','''package l1j.server.server.model.Instance;
 public class L1PcInstance {public boolean _EnchantArmorSuccess, gm=false;public Inventory inventory=new Inventory();
 public Inventory getInventory(){return inventory;}public boolean isGm(){return gm;}
 public static class Inventory{public int removals;public void removeItem(L1ItemInstance item,int count){removals++;item.count-=count;}}
 }''')
 add('l1j/server/server/model/item/L1ItemId.java','''package l1j.server.server.model.item;public class L1ItemId {
 public static final int C_SCROLL_OF_ENCHANT_ARMOR=111,Inadril_T_ScrollC=112,ENCHANT_ARMOR=113,Inadril_T_ScrollB3=114,B_SCROLL_OF_ENCHANT_ARMOR5=115; }''')
 add('l1j/server/server/model/network/core/session/CIOClient.java','''package l1j.server.server.model.network.core.session; public class CIOClient {}''')
 for cls in ('SC_ChatPacket','SC_ServerMessage','SC_SystemMessage'):
  add('l1j/server/server/model/network/core/sc/packet/'+cls+'.java',f'''package l1j.server.server.model.network.core.sc.packet;
 import l1j.server.server.model.Instance.L1PcInstance;
 public class {cls} {{public static boolean fail; public static int calls;public static void send(L1PcInstance pc, String msg){{calls++;if(fail)throw new RuntimeException("packet failed");}}
 public static void send(L1PcInstance pc,int id,String... args){{calls++;if(fail)throw new RuntimeException("packet failed");}} }}''')
 add('l1j/server/server/utils/CommonUtil.java','''package l1j.server.server.utils;public class CommonUtil {public static int random(int n){return 99;}}''')
 base='l1j/server/server/model/item/function/'
 add(base+'CreatureItemActions.java','''package l1j.server.server.model.item.function;
 public class CreatureItemActions {public static java.util.Random _random=new java.util.Random(123);
 public static java.util.logging.Logger ITEM_USE2_LOG=java.util.logging.Logger.getLogger("test");}''')
 add(base+'EnchantmentItems.java','''package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;import l1j.server.server.model.network.core.session.CIOClient;
 public class EnchantmentItems {
 public static void FailureEnchant(L1PcInstance p,L1ItemInstance t,CIOClient c){}
 public static void SuccessEnchant(L1PcInstance p,L1ItemInstance t,CIOClient c,int i){}
 public static void enchant_bless_orim(L1PcInstance p,L1ItemInstance a,L1ItemInstance b){}
 public static void enchant_insignia(L1PcInstance p,L1ItemInstance a,L1ItemInstance b){}
 public static void enchant_insignia1(L1PcInstance p,L1ItemInstance a,L1ItemInstance b){}
 public static void enchant_roomtis(L1PcInstance p,L1ItemInstance a,L1ItemInstance b){}
 public static void enchant_roomtis1(L1PcInstance p,L1ItemInstance a,L1ItemInstance b){}
 public static void enchant_sentence(L1PcInstance p,L1ItemInstance a,L1ItemInstance b){}
 public static void enchant_sentence1(L1PcInstance p,L1ItemInstance a,L1ItemInstance b){}
 public static void enchant_should(L1PcInstance p,L1ItemInstance a,L1ItemInstance b){}
 public static void enchant_snapper(L1PcInstance p,L1ItemInstance a,L1ItemInstance b){}
 public static void enchant_snapper1(L1PcInstance p,L1ItemInstance a,L1ItemInstance b){}
 }''')
 add(base+'TransformationItems.java','''package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.L1ItemInstance;
 public class TransformationItems {public static int RandomELevel(L1ItemInstance i,int id){return 1;}}''')
 add(base+'ArmorEnchantChanceCalculation.java','''package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;
 public class ArmorEnchantChanceCalculation {public static int calculate(L1PcInstance p,L1ItemInstance i,int a,int b,int c,int d){return 0;}}''')
 add(base+'ItemActionContext.java','''package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;import l1j.server.server.model.network.core.session.CIOClient;
 public class ItemActionContext {public L1PcInstance p;public L1ItemInstance a,b;public int id;
 public ItemActionContext(L1PcInstance p,L1ItemInstance a,L1ItemInstance b,int id){this.p=p;this.a=a;this.b=b;this.id=id;}
 public L1PcInstance player(){return p;}public L1ItemInstance item(){return a;}public L1ItemInstance targetItem(){return b;}
 public CIOClient client(){return null;}public int itemId(){return id;}}
 ''')
 add(base+'ItemActionResult.java','''package l1j.server.server.model.item.function;public enum ItemActionResult {STOP,COMPLETE}''')
 add(base+'ItemReceiptOperations.java','''package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;
 public class ItemReceiptOperations {public static Runnable after; public static int submitted;public static boolean commit;
 public static void UseDurableConsumeTwoInput(L1PcInstance pc,L1ItemInstance a,L1ItemInstance b,Runnable action){submitted++;
 if(a==b||a.count==0||b.count==0)throw new AssertionError("bad input");
 if(commit){a.count--;b.count--;action.run();}else after=action;
 }}''')
 add(base+'ItemActionCompletion.java','''package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;
 public class ItemActionCompletion {public static int finished;public static void finishCommittedConsumable(L1PcInstance p,L1ItemInstance i){finished++;}}
 ''')
 add(base+'TestHarness.java','''package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;import l1j.server.server.shared.GameRandom;
 import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
 class TestHarness {static int cases;static void check(boolean x){cases++;if(!x)throw new AssertionError("case="+cases);}
 public static void main(String[]args){
 L1PcInstance p=new L1PcInstance();L1ItemInstance scroll=new L1ItemInstance(),rune=new L1ItemInstance();
 GameRandom.win=false;SC_SystemMessage.fail=false;
 ItemActionResult result=ArmorEnchantScrollResolution.apply(new ItemActionContext(p,scroll,rune,3000517));
 check(result==ItemActionResult.STOP);check(ItemReceiptOperations.submitted==1);check(p.inventory.removals==0);
 check(scroll.count==1);check(rune.count==1);check(ItemActionCompletion.finished==0);
 ItemReceiptOperations.commit=true;ItemReceiptOperations.after.run();
 check(scroll.count==1);check(rune.count==1); // the mock commits only when set at submit
 check(ItemActionCompletion.finished==1);check(SC_SystemMessage.calls==1);
 // Direct-commit case: packet failure after durable double debit is nonfatal
 L1PcInstance q=new L1PcInstance();L1ItemInstance s2=new L1ItemInstance(),r2=new L1ItemInstance();
 SC_SystemMessage.fail=true;
 ItemActionResult r=ArmorEnchantScrollResolution.apply(new ItemActionContext(q,s2,r2,3000517));
 check(r==ItemActionResult.STOP);check(s2.count==0);check(r2.count==0);check(ItemActionCompletion.finished==2);
 // Success is still the original logic, no new debit path
 GameRandom.win=true;SC_SystemMessage.fail=false;
 L1PcInstance t=new L1PcInstance();L1ItemInstance s3=new L1ItemInstance(),r3=new L1ItemInstance();
 r=ArmorEnchantScrollResolution.apply(new ItemActionContext(t,s3,r3,3000517));
 check(r==ItemActionResult.COMPLETE);check(t.inventory.removals==1);check(s3.count==0);check(r3.count==1);
 System.out.println("ARMOR_TYPED_CASES="+cases+" PASS");
 }}''')
 classes=r/'classes';classes.mkdir(); files=list(r.rglob('*.java'))+[prod]
 cmd=['javac','--release','21','-encoding','UTF-8','-d',str(classes)]+list(map(str,files))
 c=subprocess.run(cmd,capture_output=True,text=True)
 if c.returncode:print(c.stdout+c.stderr);sys.exit(c.returncode)
 c=subprocess.run(['java','-cp',str(classes),'l1j.server.server.model.item.function.TestHarness'],capture_output=True,text=True)
 if c.returncode: print(c.stdout+c.stderr);sys.exit(c.returncode)
 print(c.stdout.strip())
