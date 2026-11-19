#!/usr/bin/env python3
"""Java 21: compile real StarterEquipmentGiftBox/PureShirtSoapExchange,
with typed inventory receipt fakes. Test deferred-use tail, rollback,
mailbox-style notification loss, and preserved grant payloads."""
from pathlib import Path
import os, subprocess, tempfile, textwrap
ROOT=Path(os.environ.get('SOURCE_ROOT',Path(__file__).resolve().parents[2]))
PROD=ROOT/'src/l1j/server/server/model/item/function'
PKG='l1j/server/server/model/item/function/'
with tempfile.TemporaryDirectory(prefix='rkern135_gifts_') as temp:
 base=Path(temp)/'src'; classes=Path(temp)/'classes';classes.mkdir()
 def put(path,code):
  p=base/path;p.parent.mkdir(parents=True,exist_ok=True)
  p.write_text(textwrap.dedent(code).strip()+'\n',encoding='utf-8')
 for n in ['StarterEquipmentGiftBox','PureShirtSoapExchange']:
  put(PKG+n+'.java',(PROD/(n+'.java')).read_text('utf-8'))
 put('l1j/server/server/IdFactory.java','''
package l1j.server.server;
public class IdFactory {public static IdFactory INSTANCE=new IdFactory();public int seq=5000;
public static IdFactory getInstance(){return INSTANCE;}public int nextId(){return ++seq;}}
''')
 put('l1j/server/server/templates/L1Item.java','''
package l1j.server.server.templates;
public class L1Item {public final int id;public L1Item(int id){this.id=id;}}
''')
 put('l1j/server/server/datatables/ItemTable.java','''
package l1j.server.server.datatables;
import l1j.server.server.templates.*;
public class ItemTable {public static final ItemTable TABLE=new ItemTable();
public static ItemTable getInstance(){return TABLE;}
public L1Item getTemplate(int id){return new L1Item(id);}}
''')
 put('l1j/server/server/storage/CacheD.java','''
package l1j.server.server.storage;
public class CacheD {public static String accountOrderKey(String prefix,int id){return prefix+id;}}
''')
 put('l1j/server/server/model/Instance/L1ItemInstance.java','''
package l1j.server.server.model.Instance;
import l1j.server.server.templates.*;
public class L1ItemInstance {
 public int id,itemId,count,bless,enchant,attr;public boolean identified,equipped;
 public L1ItemInstance(int id,int itemId){this.id=id;this.itemId=itemId;count=1;}
 public L1ItemInstance(L1Item tpl,int count){itemId=tpl.id;this.count=count;}
 public int getId(){return id;}public void setId(int v){id=v;}
 public int getItemId(){return itemId;}public int getCount(){return count;}
 public void setCount(int v){count=v;}public boolean isEquipped(){return equipped;}
 public void setBless(int v){bless=v;}public void setIdentified(boolean v){identified=v;}
 public void setEnchantLevel(int v){enchant=v;}public int getEnchantLevel(){return enchant;}
 public void setAttrEnchantLevel(int v){attr=v;}public String getLogName(){return "reward"+itemId;}
}
''')
 put('l1j/server/server/model/Instance/L1PcInstance.java','''
package l1j.server.server.model.Instance;
import java.util.*;
public class L1PcInstance {public Inventory inv=new Inventory();
 public int getId(){return 77;}public Inventory getInventory(){return inv;}
 public boolean isCrown(){return true;}public boolean isKnight(){return false;}
 public boolean isElf(){return false;}public boolean isWizard(){return false;}
 public boolean isDarkelf(){return false;}public boolean isDragonknight(){return false;}
 public boolean isBlackwizard(){return false;}
 public static class Inventory {public final Map<Integer,L1ItemInstance> items=new HashMap<>();
  public L1ItemInstance getItem(int id){return items.get(id);}}
}
''')
 put('l1j/server/server/model/L1InventoryTransaction.java','''
package l1j.server.server.model;
import java.util.*;import l1j.server.server.model.Instance.*;
public class L1InventoryTransaction implements AutoCloseable {
 public static L1InventoryTransaction last;
 public final L1PcInstance pc;public final List<L1ItemInstance> grants=new ArrayList<>();
 public final List<L1ItemInstance> debits=new ArrayList<>();public Runnable after,fail;
 public L1InventoryTransaction(L1PcInstance pc){this.pc=pc;last=this;}
 public boolean debit(L1ItemInstance item,int count){if(item==null||item.count<count
  || pc.getInventory().getItem(item.id)!=item)return false;debits.add(item);return true;}
 public L1ItemInstance grant(L1ItemInstance item){grants.add(item);return item;}
 public L1ItemInstance grant(int itemId,int count){L1ItemInstance i=new L1ItemInstance(itemId,count);
  i.id=5000+grants.size();grants.add(i);return i;}
 public void onFailure(Runnable cb){fail=cb;}public void commit(Runnable cb){after=cb;}
 public void succeed(){for(L1ItemInstance input:debits){input.count--;if(input.count==0)pc.inv.items.remove(input.id);}
  for(L1ItemInstance reward:grants)pc.inv.items.put(reward.id,reward);
  if(after!=null)after.run();}
 public void rollback(){if(fail!=null)fail.run();}
 public void close(){}
}
''')
 put('l1j/server/server/model/network/core/cs/packet/User.java','''
package l1j.server.server.model.network.core.cs.packet;
import l1j.server.server.model.Instance.*;import l1j.server.server.model.*;
public class User {public static L1InventoryTransaction CheckAndSetPendingUseETCItem(L1PcInstance pc,String k){return new L1InventoryTransaction(pc);}}
''')
 put('l1j/server/server/model/network/core/cs/packet/CDB.java','''
package l1j.server.server.model.network.core.cs.packet;
import l1j.server.server.model.Instance.*;import l1j.server.server.model.*;
public class CDB {public static void RequestUseItem(L1PcInstance pc,L1InventoryTransaction tx,Runnable callback){tx.commit(callback);}}
''')
 put('l1j/server/server/model/network/core/sc/packet/SC_ServerMessage.java','''
package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.*;
public class SC_ServerMessage {public static int sends,failEvery;
 public static void send(L1PcInstance pc,int id){sends++;}
 public static void send(L1PcInstance pc,int id,String value){sends++;
 if(failEvery>0&&sends%failEvery==0)throw new IllegalStateException("send failed");}}
''')
 put('l1j/server/server/model/network/core/sc/packet/SC_SystemMessage.java','''
package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.*;
public class SC_SystemMessage {public static void send(L1PcInstance pc,String value) {}}
''')
 put(PKG+'ItemActionCompletion.java','''
package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
public class ItemActionCompletion {public static int calls;
public static void finishCommittedConsumable(L1PcInstance pc,L1ItemInstance item){calls++;}}
''')
 put(PKG+'TestGiftReceipt.java','''
package l1j.server.server.model.item.function;
import l1j.server.server.model.*;import l1j.server.server.model.Instance.*;
import l1j.server.server.model.network.core.sc.packet.*;
public class TestGiftReceipt {static int checks;
 static void ok(boolean b,String msg){checks++;if(!b)throw new AssertionError(msg);}
 static L1PcInstance pc;static L1ItemInstance input,shirt;
 static void reset(){ItemActionCompletion.calls=0;SC_ServerMessage.sends=0;SC_ServerMessage.failEvery=0;
  L1InventoryTransaction.last=null;pc=new L1PcInstance();input=new L1ItemInstance(501,30071);
  pc.inv.items.put(input.id,input);}
 static void starter(boolean failNotification){
  reset();SC_ServerMessage.failEvery=failNotification?1:0;
  StarterEquipmentGiftBox.openStarterKit(pc,input);
  L1InventoryTransaction tx=L1InventoryTransaction.last;
  ok(tx!=null,"starter receipt exists");
  ok(tx.after!=null,"starter callback deferred");
  ok(ItemActionCompletion.calls==0&&input.count==1,"starter no precommit use");
  ok(pc.inv.items.size()==1,"starter no precommit rewards");
  int count=tx.grants.size();ok(count>=20,"starter grants prepared");
  tx.succeed();
  ok(ItemActionCompletion.calls==1&&input.count==0,"starter committed tail");
  ok(pc.inv.items.size()==count,"starter rewards retained");
  ok(SC_ServerMessage.sends==count,"starter attempted every notification");
 }
 static void pure(boolean failNotification){
  reset();input=new L1ItemInstance(501,30116);shirt=new L1ItemInstance(502,22340);
  shirt.enchant=9;pc.inv.items.put(input.id,input);pc.inv.items.put(shirt.id,shirt);
  SC_ServerMessage.failEvery=failNotification?1:0;
  PureShirtSoapExchange.exchangePureShirt(pc,input,shirt);
  L1InventoryTransaction tx=L1InventoryTransaction.last;
  ok(tx!=null&&tx.after!=null,"shirt receipt staged");
  ok(ItemActionCompletion.calls==0&&input.count==1&&shirt.count==1,"shirt precommit unchanged");
  ok(tx.grants.size()==1,"shirt exactly one reward");
  tx.succeed();ok(input.count==0&&shirt.count==0,"shirt and reagent debited");
  ok(ItemActionCompletion.calls==1,"shirt committed use tail");
  ok(tx.grants.get(0).enchant==9,"shirt enchant level carried");
  ok(SC_ServerMessage.sends==1,"shirt status attempted");
 }
 public static void main(String[] args){
  java.util.logging.Logger.getLogger(StarterEquipmentGiftBox.class.getName()).setLevel(java.util.logging.Level.OFF);
  java.util.logging.Logger.getLogger(PureShirtSoapExchange.class.getName()).setLevel(java.util.logging.Level.OFF);
  starter(false);starter(true);pure(false);pure(true);
  reset();StarterEquipmentGiftBox.openStarterKit(pc,input);
  L1InventoryTransaction tx=L1InventoryTransaction.last;tx.rollback();
  ok(input.count==1&&pc.inv.items.size()==1&&ItemActionCompletion.calls==0,"starter rollback");
  reset();input=new L1ItemInstance(501,30116);shirt=new L1ItemInstance(502,22340);
  pc.inv.items.put(input.id,input);pc.inv.items.put(shirt.id,shirt);
  PureShirtSoapExchange.exchangePureShirt(pc,input,shirt);
  tx=L1InventoryTransaction.last;tx.rollback();
  ok(input.count==1&&shirt.count==1&&ItemActionCompletion.calls==0,"shirt rollback");
  System.out.println("STARTER_SHIRT_JAVA21_ASSERTIONS="+checks);
 }
}
''')
 files=[str(f) for f in base.rglob('*.java')]
 p=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(classes),*files],capture_output=True,text=True)
 if p.returncode:print(p.stderr[-13000:]);raise SystemExit('starter/shirt typed compile failed')
 p=subprocess.run(['java','-cp',str(classes),'l1j.server.server.model.item.function.TestGiftReceipt'],capture_output=True,text=True)
 print(p.stdout,end='')
 if p.returncode:print(p.stderr[-13000:]);raise SystemExit('starter/shirt test failed')
