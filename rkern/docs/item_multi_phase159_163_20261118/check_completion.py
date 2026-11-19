#!/usr/bin/env python3
"""Typed Java21 test of production item completion: persistence-before-packet and dirty admission."""
import pathlib, subprocess, tempfile, sys
src=pathlib.Path(sys.argv[1]);prod=src/'l1j/server/server/model/item/function/ItemActionCompletion.java'
with tempfile.TemporaryDirectory() as t:
 root=pathlib.Path(t);base='l1j/server/server/model/item/function/'
 def add(p,body):
  x=root/p;x.parent.mkdir(parents=True,exist_ok=True);x.write_text(body,encoding='utf8')
 add('l1j/server/server/templates/L1EtcItem.java','''package l1j.server.server.templates;
 public class L1EtcItem {public int type2=0,delayEffect=1;public int getType2(){return type2;}public int get_delayEffect(){return delayEffect;}}''')
 add('l1j/server/server/model/Instance/L1ItemInstance.java','''package l1j.server.server.model.Instance;
 import java.sql.Timestamp;import l1j.server.server.templates.L1EtcItem;
 public class L1ItemInstance {public L1EtcItem data=new L1EtcItem();public int id=400,count=1,charge=4;public Timestamp used;
 public L1EtcItem getItem(){return data;}public int getId(){return id;}
 public int getChargeCount(){return charge;}public void setChargeCount(int c){charge=c;}
 public Timestamp getLastUsed(){return used;}public void setLastUsed(Timestamp t){used=t;}}
 ''')
 add('l1j/server/server/model/L1PcInventory.java','''package l1j.server.server.model;
 import l1j.server.server.model.Instance.L1ItemInstance;
 public class L1PcInventory {public static final int COL_CHARGE_COUNT=32,COL_DELAY_EFFECT=256;
 public int saves,updates,removes,lastMask,lastUpdateMask;public boolean saveFail,packetFail,gateFail;
 public final StringBuilder trace=new StringBuilder();public L1ItemInstance item;
 public L1ItemInstance getItem(int id){return item!=null&&item.getId()==id?item:null;}
 public void saveItem(L1ItemInstance i,int mask){saves++;lastMask=mask;trace.append('S');if(saveFail)throw new IllegalStateException("save failed");}
 public void updateItem(L1ItemInstance i,int mask){updates++;lastUpdateMask=mask;trace.append('U');if(gateFail)throw new IllegalStateException("Item mutation refused while CacheD transfer pending");if(packetFail)throw new RuntimeException("network packet failed");}
 public void removeItem(L1ItemInstance i,int count){removes++;trace.append('R');i.count-=count;}
 }''')
 add('l1j/server/server/model/Instance/L1PcInstance.java','''package l1j.server.server.model.Instance;
 import l1j.server.server.model.L1PcInventory;
 public class L1PcInstance {public final L1PcInventory inv=new L1PcInventory();public L1PcInventory getInventory(){return inv;}}''')
 add('l1j/server/server/model/L1ItemDelay.java','''package l1j.server.server.model;
 import l1j.server.server.model.Instance.*;
 public class L1ItemDelay {public static int calls;public static void onItemUse(L1PcInstance p,L1ItemInstance i){calls++;p.inv.trace.append('D');}}''')
 add(base+'ItemActionContext.java','''package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;
 public class ItemActionContext {L1PcInstance p;L1ItemInstance i;int id;boolean d;
 public ItemActionContext(L1PcInstance p,L1ItemInstance i,int id,boolean d){this.p=p;this.i=i;this.id=id;this.d=d;}
 public L1PcInstance player(){return p;}public L1ItemInstance item(){return i;}public int itemId(){return id;}public boolean delayEffect(){return d;}}''')
 add(base+'TestHarness.java','''package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;import l1j.server.server.model.*;import java.sql.Timestamp;
 class TestHarness {static int n;static void ok(boolean x){n++;if(!x)throw new AssertionError("case "+n);}
 static void validCharge(boolean packetFail){
 L1PcInstance p=new L1PcInstance();L1ItemInstance i=new L1ItemInstance();p.inv.item=i;p.inv.packetFail=packetFail;
 ItemActionCompletion.finish(new ItemActionContext(p,i,410008,true));
 ok(i.charge==3);ok(i.used!=null);ok(p.inv.saves==1);ok(p.inv.updates==1);
 ok(p.inv.lastMask==(L1PcInventory.COL_CHARGE_COUNT|L1PcInventory.COL_DELAY_EFFECT));
 ok(p.inv.lastUpdateMask==L1PcInventory.COL_CHARGE_COUNT);ok(p.inv.trace.toString().equals("SUD"));
 }
 static void failCharge(){
 L1PcInstance p=new L1PcInstance();L1ItemInstance i=new L1ItemInstance();p.inv.item=i;p.inv.saveFail=true;
 boolean failed=false;try{ItemActionCompletion.finish(new ItemActionContext(p,i,410008,true));}catch(IllegalStateException e){failed=true;}
 ok(failed);ok(i.charge==4);ok(i.used==null);ok(p.inv.updates==0);ok(p.inv.trace.toString().equals("S"));
 }
 static void committed(boolean failPacket,boolean failSave){
 L1PcInstance p=new L1PcInstance();L1ItemInstance i=new L1ItemInstance();p.inv.item=i;p.inv.packetFail=failPacket;p.inv.saveFail=failSave;
 boolean failure=false;try{ItemActionCompletion.finishCommittedConsumable(p,i);}catch(IllegalStateException e){failure=true;}
 ok(failure==failSave);ok(i.used==null==failSave);ok(p.inv.saves==1);ok(p.inv.updates==(failSave?0:1));
 if(!failSave)ok(p.inv.trace.toString().equals("SUD"));
 }
 public static void main(String[] args){for(int j=0;j<60;j++){
 validCharge(false);validCharge(true);failCharge();committed(false,false);committed(true,false);committed(false,true);
 }
 System.out.println("COMPLETION_TYPED_CASES="+n+" PASS");}
 }''')
 files=list(root.rglob('*.java'))+[prod];out=root/'out';out.mkdir()
 c=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(out),*map(str,files)],capture_output=True,text=True)
 if c.returncode:raise RuntimeError(c.stdout+c.stderr)
 c=subprocess.run(['java','-cp',str(out),'l1j.server.server.model.item.function.TestHarness'],capture_output=True,text=True)
 if c.returncode:raise RuntimeError(c.stdout+c.stderr)
 print(c.stdout.strip())
