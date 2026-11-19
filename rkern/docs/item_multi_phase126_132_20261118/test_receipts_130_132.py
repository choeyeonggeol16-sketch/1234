#!/usr/bin/env python3
'''Compile the *production bodies* of legacy gift, hero and clan reward receipts
against tiny Java 21 stubs; fault injection checks precommit, rollback and packet
failure separation. Not a real MySQL or full dependency compilation test.'''
from pathlib import Path
import tempfile, subprocess, textwrap, os, re
root=Path(os.getenv('SOURCE_ROOT', Path(__file__).resolve().parents[2]))
prod=root/'src/l1j/server/server/model/item/function'
with tempfile.TemporaryDirectory(prefix='rkern130_') as tmp:
 src=Path(tmp)/'src'; out=Path(tmp)/'out'; out.mkdir()
 def put(path,content):
  p=src/path;p.parent.mkdir(parents=True,exist_ok=True)
  p.write_text(textwrap.dedent(content).strip()+'\n', encoding='utf-8')
 for name in ('LegacyGiftBoxes','HeroPackageReceipt','ClanRewardScrollItems'):
  text=(prod/(name+'.java')).read_text('utf-8')
  match=re.search(r'\bfinal class '+name+r'\b',text)
  assert match, name
  # Original production class body unchanged, only imports are reduced for the isolated fixture.
  pre='''package l1j.server.server.model.item.function;
import java.util.logging.*;
import l1j.server.Config;
import l1j.server.server.datatables.*;
import l1j.server.server.model.*;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.network.core.cs.packet.*;
import l1j.server.server.model.network.core.sc.packet.*;
import l1j.server.server.storage.CacheD;
import l1j.server.server.templates.*;
import static l1j.server.server.model.item.function.CreatureItemActions._random;
'''
  put('l1j/server/server/model/item/function/'+name+'.java',pre+text[match.start():])
 put('l1j/server/Config.java','''package l1j.server;public final class Config{
 public static int 혈맹포인트최대=50, 혈맹포인트최소=20;}''')
 put('l1j/server/server/templates/L1Item.java','''package l1j.server.server.templates;
 public final class L1Item{public final int id;public L1Item(int i){id=i;}}''')
 put('l1j/server/server/model/Instance/L1ItemInstance.java','''package l1j.server.server.model.Instance;
 import l1j.server.server.templates.L1Item;
 public class L1ItemInstance{public int objectId,itemId;private int count=1;
 public L1ItemInstance(int obj,int id){objectId=obj;itemId=id;}
 public L1ItemInstance(L1Item templ,int count){objectId=700;itemId=templ.id;this.count=count;}
 public int getId(){return objectId;} public int getItemId(){return itemId;}
 public int getCount(){return count;}public String getLogName(){return "reward-"+itemId;}
 public void setId(int id){objectId=id;}public void setBless(int bless){}public void setIdentified(boolean b){} }
 ''')
 put('l1j/server/server/model/Instance/L1PcInstance.java','''package l1j.server.server.model.Instance;
 import l1j.server.server.model.L1Clan;
 public class L1PcInstance {
 public static class Inv {public L1ItemInstance current;
 public L1ItemInstance getItem(int id){return current!=null && current.getId()==id?current:null;}
 public int getSize(){return 12;}public int getWeight100(){return 30;}}
 public Inv inventory=new Inv();public final int id=41;public L1Clan clan;
 public Inv getInventory(){return inventory;}public int getId(){return id;}public String getName(){return "test";}
 public boolean isWarrior(){return true;}public boolean isKnight(){return false;}
 public boolean isDragonknight(){return false;}public boolean isCrown(){return false;}
 public boolean isWizard(){return false;}public boolean isBlackwizard(){return false;}
 public boolean isElf(){return false;}public boolean isDarkelf(){return false;}
 public L1Clan getClan(){return clan;}public int getClanid(){return clan==null?0:1;}
 public void sendPackets(String s){}}''')
 put('l1j/server/server/model/L1Clan.java','''package l1j.server.server.model;
 import l1j.server.server.model.Instance.L1PcInstance;
 public class L1Clan {public int points=0;public static boolean failState;
 public void addWarPointCommitted(int n){if(failState)throw new IllegalStateException("economic fault");points+=n;}
 public L1PcInstance[] getOnlineClanMember(){return new L1PcInstance[]{new L1PcInstance(),new L1PcInstance()};}}''')
 put('l1j/server/server/model/L1InventoryTransaction.java','''package l1j.server.server.model;
 import l1j.server.server.model.Instance.*;
 public final class L1InventoryTransaction implements AutoCloseable {
 public static Runnable callback, failure;public static int debits,grants,requestCount;
 public static int[] granted=new int[8];public static boolean reject;
 public static L1InventoryTransaction begin(L1PcInstance pc,String key){return new L1InventoryTransaction();}
 public boolean debit(L1ItemInstance i,int n){debits++;return !reject;}
 public L1ItemInstance grant(int id,int n){grants++;granted[grants]=id;return new L1ItemInstance(200+grants,id);}
 public L1ItemInstance grant(L1ItemInstance i){grants++;granted[grants]=i.itemId;return i;}
 public void onFailure(Runnable r){failure=r;}
 public void commit(Runnable r){requestCount++;callback=r;}
 public void close(){} public static void reset(){callback=null;failure=null;debits=grants=requestCount=0;reject=false;}
 public static void complete(){Runnable r=callback;callback=null;if(r==null)throw new AssertionError("no callback");r.run();}
 }''')
 put('l1j/server/server/model/network/core/cs/packet/User.java','''package l1j.server.server.model.network.core.cs.packet;
 import l1j.server.server.model.*; import l1j.server.server.model.Instance.*;
 public final class User{public static L1InventoryTransaction CheckAndSetPendingUseETCItem(L1PcInstance p,String k){return L1InventoryTransaction.begin(p,k);}}
 ''')
 put('l1j/server/server/model/network/core/cs/packet/CDB.java','''package l1j.server.server.model.network.core.cs.packet;
 import l1j.server.server.model.*;import l1j.server.server.model.Instance.*;
 public final class CDB{public static void RequestUseItem(L1PcInstance p,L1InventoryTransaction t,Runnable r){t.commit(r);}}''')
 put('l1j/server/server/storage/CacheD.java','''package l1j.server.server.storage;
 public final class CacheD{public static String accountOrderKey(String s,int id){return s+id;}}''')
 put('l1j/server/server/datatables/ItemTable.java','''package l1j.server.server.datatables;
 import l1j.server.server.templates.L1Item;
 public final class ItemTable {public static ItemTable getInstance(){return new ItemTable();}
 public L1Item getTemplate(int n){return new L1Item(n);}}''')
 put('l1j/server/server/datatables/ClanTable.java','''package l1j.server.server.datatables;
 import l1j.server.server.model.*;
 public final class ClanTable {public static int stages;
 public static void stageWarPointDelta(L1InventoryTransaction tx,L1Clan c,int n){stages++;}}''')
 put('l1j/server/server/IdFactory.java','''package l1j.server.server;
 public final class IdFactory{public static IdFactory getInstance(){return new IdFactory();}public int nextId(){return 111111;}}''')
 put('l1j/server/server/model/network/core/sc/packet/SC_SystemMessage.java','''package l1j.server.server.model.network.core.sc.packet;
 import l1j.server.server.model.Instance.L1PcInstance;
 public final class SC_SystemMessage {public static int calls;public static int failMask;
 public static void send(L1PcInstance p,String m){calls++;if((failMask & (1<<calls))!=0)throw new IllegalStateException("display failure "+calls);}
 public static Object nativeWriter(String m){return m;} }''')
 put('l1j/server/server/model/network/core/sc/packet/SC_ServerMessage.java','''package l1j.server.server.model.network.core.sc.packet;
 import l1j.server.server.model.Instance.L1PcInstance;
 public final class SC_ServerMessage {public static int calls;public static boolean fail;
 public static void send(L1PcInstance p,int id){calls++;if(fail)throw new IllegalStateException();}
 public static void send(L1PcInstance p,int id,String name){calls++;if(fail)throw new IllegalStateException("display");}}''')
 put('l1j/server/server/model/network/core/sc/packet/SC_PacketBox.java','''package l1j.server.server.model.network.core.sc.packet;
 public final class SC_PacketBox {public static Object nativeGreenWriter(String s){return s;}}''')
 put('l1j/server/server/model/network/core/broadcast/CAsyncSendConstBuffer.java','''package l1j.server.server.model.network.core.broadcast;
 public final class CAsyncSendConstBuffer implements AutoCloseable{public static boolean failCreate;
 public CAsyncSendConstBuffer(Object buf,int a,int b){if(failCreate)throw new IllegalStateException("buffer creation");}
 public void close(){} }''')
 put('l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java','''package l1j.server.server.model.network.core.broadcast;
 import l1j.server.server.model.Instance.L1PcInstance;
 public final class CSharedBroadcast{public static int sends;public static boolean fail;
 public static void sendRecipient(CAsyncSendConstBuffer c,L1PcInstance p){sends++;if(fail)throw new IllegalStateException("broadcast failed");}}''')
 put('l1j/server/server/model/item/function/CreatureItemActions.java','''package l1j.server.server.model.item.function;
 public final class CreatureItemActions {public static final java.util.Random _random=new java.util.Random(33);}''')
 put('l1j/server/server/model/item/function/SpecialConsumableRouter.java','''package l1j.server.server.model.item.function; public final class SpecialConsumableRouter {}''')
 put('l1j/server/server/model/item/function/ClanBuffScrollTransactions.java','''package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;
 public final class ClanBuffScrollTransactions{public static void useClanBuff(L1PcInstance p,L1ItemInstance i){}}''')
 put('l1j/server/server/model/item/function/ItemActionCompletion.java','''package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;
 public final class ItemActionCompletion{public static int count;
 public static void finishCommittedConsumable(L1PcInstance pc,L1ItemInstance item){count++;}}''')
 put('l1j/server/server/model/item/function/ItemActionResult.java','''package l1j.server.server.model.item.function;
 public enum ItemActionResult{STOP,COMPLETE}''')
 put('l1j/server/server/model/item/function/ItemActionContext.java','''package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;
 public final class ItemActionContext{private final L1PcInstance pc;private final L1ItemInstance item;
 public ItemActionContext(L1PcInstance p,L1ItemInstance i){pc=p;item=i;}
 public L1PcInstance player(){return pc;}public L1ItemInstance item(){return item;}}''')
 put('l1j/server/server/model/item/function/Test130.java',r'''
 package l1j.server.server.model.item.function;
 import l1j.server.server.model.*;import l1j.server.server.model.Instance.*;
 import l1j.server.server.model.network.core.sc.packet.*;
 import l1j.server.server.model.network.core.broadcast.*;
 public final class Test130{
 static int assertions;
 static void eq(boolean b,String m){assertions++;if(!b)throw new AssertionError(m);}
 static void reset(){L1InventoryTransaction.reset();ItemActionCompletion.count=0;SC_SystemMessage.calls=0;
 SC_SystemMessage.failMask=0;SC_ServerMessage.calls=0;SC_ServerMessage.fail=false;
 CAsyncSendConstBuffer.failCreate=false;CSharedBroadcast.sends=0;CSharedBroadcast.fail=false;L1Clan.failState=false;}
 static L1PcInstance player(int itemId){L1PcInstance pc=new L1PcInstance();pc.inventory.current=new L1ItemInstance(121,itemId);return pc;}
 public static void main(String[] args){
 for(int id:new int[]{41303,41304})for(int mask=0;mask<4;mask++){
  reset();L1PcInstance pc=player(id);L1ItemInstance item=pc.inventory.current;
  eq(LegacyGiftBoxes.open(new ItemActionContext(pc,item),id)==ItemActionResult.STOP,"legacy STOP");
  eq(L1InventoryTransaction.requestCount==1,"receipt submitted");
  eq(ItemActionCompletion.count==0,"precommit no tail");
  eq(L1InventoryTransaction.granted[1]==40308,"adena staged");
  SC_SystemMessage.failMask=mask==0?0:mask==1?2:mask==2?4:6;
  L1InventoryTransaction.complete();
  eq(ItemActionCompletion.count==1,"one completion tail");
  eq(SC_SystemMessage.calls==2,"both notices attempted");
 }
 for(int id:new int[]{41303,41304}){
  reset();L1PcInstance pc=player(id);
  LegacyGiftBoxes.open(new ItemActionContext(pc,pc.inventory.current),id);
  L1InventoryTransaction.callback=null;eq(ItemActionCompletion.count==0,"rollback tail must not fire");
  reset();pc=player(id);L1InventoryTransaction.reject=true;
  eq(LegacyGiftBoxes.open(new ItemActionContext(pc,pc.inventory.current),id)==ItemActionResult.STOP,"reject STOP");
  eq(ItemActionCompletion.count==0,"reject no completion");
 }
 for(int faulty=0;faulty<2;faulty++){
  reset();L1PcInstance pc=player(753);
  HeroPackageReceipt.useHeroPackageAtomic(pc,pc.inventory.current);
  eq(L1InventoryTransaction.requestCount==1,"hero transaction");
  eq(L1InventoryTransaction.granted[1]==832,"hero class reward unchanged");
  eq(ItemActionCompletion.count==0,"hero precommit no tail");
  SC_ServerMessage.fail=faulty!=0;
  L1InventoryTransaction.complete();eq(ItemActionCompletion.count==1,"hero committed once");
 }
 for(int mode=0;mode<4;mode++){
  reset();L1PcInstance pc=player(145010);pc.clan=new L1Clan();
  ClanRewardScrollItems.use(pc,pc.inventory.current,145010,null);
  eq(L1InventoryTransaction.requestCount==1,"clan point transaction");
  eq(pc.clan.points==0,"no points precommit");
  eq(ItemActionCompletion.count==0,"clan tail not before commit");
  CSharedBroadcast.fail=mode==1;CAsyncSendConstBuffer.failCreate=mode==2;
  if(mode==3)L1Clan.failState=true;
  if(mode==3){boolean threw=false;try{L1InventoryTransaction.complete();}catch(IllegalStateException ex){threw=true;}
   eq(threw,"state fail propagates");eq(pc.clan.points==0,"no fake state points");}
  else {L1InventoryTransaction.complete();eq(pc.clan.points>=20,"points applied");}
  eq(ItemActionCompletion.count==1,"committed clan tail once");
 }
 System.out.println("PHASE130_132_TYPED_ASSERTIONS="+assertions);
 }
 }
 ''')
 files=list(src.rglob('*.java'))
 run=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(out),*map(str,files)],capture_output=True,text=True)
 if run.returncode:print(run.stderr[-10000:]);raise SystemExit(run.returncode)
 run=subprocess.run(['java','-cp',str(out),'l1j.server.server.model.item.function.Test130'],capture_output=True,text=True)
 if run.returncode:print(run.stdout[-1000:],run.stderr[-10000:]);raise SystemExit(run.returncode)
 print(run.stdout.strip())
