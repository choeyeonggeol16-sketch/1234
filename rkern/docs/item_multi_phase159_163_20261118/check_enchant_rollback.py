#!/usr/bin/env python3
"""Typed test of the actual production EnchantmentOutcomeEffects.java using Java21 stubs."""
import subprocess, sys, tempfile
from pathlib import Path
src=Path(sys.argv[1]); prod=src/'l1j/server/server/model/item/function/EnchantmentOutcomeEffects.java'
assert prod.exists()
with tempfile.TemporaryDirectory(prefix='rk_enchant_') as tmp:
 r=Path(tmp)
 def add(name,body):
  p=r/name; p.parent.mkdir(parents=True, exist_ok=True); p.write_text(body)
 add('l1j/server/Config.java','''package l1j.server; public final class Config {public static boolean MASTERENCHANTMESS=false; public static int LOGGING_WEAPON_ENCHANT=0, LOGGING_ARMOR_ENCHANT=0;}''')
 add('l1j/server/server/model/Instance/L1ItemInstance.java','''package l1j.server.server.model.Instance;
 public class L1ItemInstance {
 public static class Item {public int id=1000,type2=1,type=0,safe=0; public int getType2(){return type2;} public int getType(){return type;} public int getItemId(){return id;} public int get_safeenchant(){return safe;} }
 public Item data=new Item(); public int lv=1,count=1; public boolean equipped=false,identified=true;
 public Item getItem(){return data;} public String getName(){return "TEST";} public String getLogName(){return "TEST";}
 public int getEnchantLevel(){return lv;} public void setEnchantLevel(int n){lv=n;} public boolean isIdentified(){return identified;} public boolean isEquipped(){return equipped;} public int getCount(){return count;}
 }''')
 add('l1j/server/server/model/CUserInventory.java','''package l1j.server.server.model;
 import l1j.server.server.model.Instance.L1ItemInstance;
 public class CUserInventory { public static final int COL_ENCHANTLVL=4;
 public int saves,updates,removes; public int packetFailMode=0; public boolean saveFails;
 public void saveItem(L1ItemInstance x,int col) {saves++;if(saveFails) throw new IllegalStateException("DB admission failed");}
 public void updateItem(L1ItemInstance x,int col){updates++;if(packetFailMode==1)throw new RuntimeException("packet failure");if(packetFailMode==2)throw new IllegalStateException("Item mutation refused while CacheD transfer pending");}
 public void removeItem(L1ItemInstance x,int n){removes++;x.count-=n;}
 public void setEquipped(L1ItemInstance x,boolean v){x.equipped=v;}
 }''')
 add('l1j/server/server/model/Instance/L1PcInstance.java','''package l1j.server.server.model.Instance;
 import l1j.server.server.model.CUserInventory;
 public class L1PcInstance {public CUserInventory inv=new CUserInventory(); public CUserInventory getInventory(){return inv;}
 public final AC ac=new AC(); public final Resistance res=new Resistance();
 public AC getAC(){return ac;}public Resistance getResistance(){return res;}
 public static class AC {public int val;public void addAc(int i){val+=i;}}
 public static class Resistance {public int mr;public void addMr(int i){mr+=i;}}
 }''')
 add('l1j/server/server/model/network/core/session/CIOClient.java','''package l1j.server.server.model.network.core.session; public class CIOClient {}''')
 add('l1j/server/server/monitor/LoggerInstance.java','''package l1j.server.server.monitor;
 import l1j.server.server.model.Instance.*;
 public final class LoggerInstance {public static boolean fail; public static int called;
 private static final LoggerInstance INSTANCE=new LoggerInstance(); public static LoggerInstance getInstance(){return INSTANCE;}
 public void addEnchant(L1PcInstance pc,L1ItemInstance item,boolean success){called++;if(fail)throw new RuntimeException("audit unavailable");}
 }''')
 for cls, methods in {
 'SC_OwnCharStatus':'public static int calls; public static void send(L1PcInstance p){calls++;}',
 'SC_PacketBox':'public static void broadcastGreen(String s){}',
 'SC_ServerMessage':'public static int calls; public static void send(L1PcInstance p,int id,String... args){calls++;}',
 'SC_SystemMessage':'public static void broadcast(String s){}',
 'SC_SPMR':'public static void send(L1PcInstance p){}',
 }.items():
  add('l1j/server/server/model/network/core/sc/packet/'+cls+'.java',f'package l1j.server.server.model.network.core.sc.packet; import l1j.server.server.model.Instance.L1PcInstance; public class {cls} {{{methods}}}')
 # Compile the *unchanged* actual production file alongside stubs.
 add('l1j/server/server/model/item/function/TestHarness.java','''package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;
 import l1j.server.server.monitor.LoggerInstance;
 public class TestHarness {static int cases;
 static void check(boolean ok){cases++;if(!ok)throw new AssertionError("case="+cases);}
 public static void main(String[] args){
   L1PcInstance pc=new L1PcInstance(); L1ItemInstance item=new L1ItemInstance();
   pc.inv.packetFailMode=1; item.lv=1; item.data.safe=0;
   EnchantmentOutcomeEffects.SuccessEnchant(pc,item,null,1);
   check(item.lv==2);check(pc.inv.saves==1);check(pc.inv.updates==1);
   check(LoggerInstance.called==1);
   pc.inv.packetFailMode=2;int before=pc.inv.saves;boolean refused=false;
   try{EnchantmentOutcomeEffects.SuccessEnchant(pc,item,null,1);}catch(IllegalStateException x){refused=true;}
   check(refused); check(pc.inv.saves==before+1);
   L1PcInstance pc2=new L1PcInstance();L1ItemInstance item2=new L1ItemInstance();
   pc2.inv.saveFails=true; boolean dbFailed=false;
   try{EnchantmentOutcomeEffects.SuccessEnchant(pc2,item2,null,1);}catch(IllegalStateException x){dbFailed=true;}
   check(dbFailed);check(pc2.inv.updates==0);check(item2.lv==1);
   L1PcInstance pc3=new L1PcInstance();L1ItemInstance item3=new L1ItemInstance();
   item3.data.id=1115;item3.data.type2=1;item3.lv=4;pc3.inv.packetFailMode=1;
   EnchantmentOutcomeEffects.FailureEnchant(pc3,item3,null);
   check(item3.lv==0);check(pc3.inv.saves==1);check(pc3.inv.updates==1);
   L1PcInstance pc4=new L1PcInstance();L1ItemInstance item4=new L1ItemInstance();
   LoggerInstance.fail=true;
   EnchantmentOutcomeEffects.FailureEnchant(pc4,item4,null);
   check(pc4.inv.removes==1);check(item4.count==0);
   System.out.println("ENCHANT_TYPED_CASES="+cases+" PASS");
 }
 }''')
 files=list(r.rglob('*.java'))+[prod]
 classes=r/'classes';classes.mkdir();
 cmd=['javac','--release','21','-encoding','UTF-8','-d',str(classes)]+[str(x) for x in files]
 c=subprocess.run(cmd,capture_output=True,text=True)
 if c.returncode:print(c.stdout+c.stderr);sys.exit(c.returncode)
 c=subprocess.run(['java','-cp',str(classes),'l1j.server.server.model.item.function.TestHarness'],capture_output=True,text=True)
 if c.returncode:print(c.stdout+c.stderr);sys.exit(c.returncode)
 print(c.stdout.strip())
