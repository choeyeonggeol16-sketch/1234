from pathlib import Path
import sys
base=Path(sys.argv[1]).resolve()
root=Path(sys.argv[2]).resolve()
p=root/'l1j/server/server/model/item/function'
p.mkdir(parents=True,exist_ok=True)
src=base/'src/l1j/server/server/model/item/function'
def extract(s,start):
 a=s.index(start); br=s.index('{',a); dep=1;i=br+1
 while dep and i<len(s):
  if s[i]=='{':dep+=1
  elif s[i]=='}':dep-=1
  i+=1
 assert dep==0
 return s[a:i]
w=extract((src/'NpcWandAndSpawnItems.java').read_text(),'public static void useNpcSpownWand(').replace('l1j.server.server.model.L1InventoryTransaction', 'L1InventoryTransaction')
(p/'NpcWandAndSpawnItems.java').write_text('''package l1j.server.server.model.item.function;
import java.lang.reflect.Constructor;
import l1j.server.server.model.Instance.L1NpcInstance;
public final class NpcWandAndSpawnItems {'''+w+'\n}')
pet=(src/'PetAndSummonItems.java').read_text();t=extract(pet,'static ItemActionResult handleSummonMonster(')
(p/'PetAndSummonItems.java').write_text('''package l1j.server.server.model.item.function;
public final class PetAndSummonItems {
private static final int[] SUMMON_IDS={7320072,7320073,7320074,7320075,7320076,7320077,7320078,7320079,7320080,7320081,7320000};
'''+t+'\n}')
(p/'L1Npc.java').write_text('''package l1j.server.server.model.item.function;
public final class L1Npc { public static boolean invalid; public String getImpl(){return invalid?"MissingNpc":"TestNpc";} }''')
inst=root/'l1j/server/server/model/Instance';inst.mkdir(parents=True,exist_ok=True)
(inst/'L1NpcInstance.java').write_text('''package l1j.server.server.model.Instance;
public class L1NpcInstance {
 public static boolean failStart;
 public void setId(int x){} public void setMap(int x){} public void setX(int x){} public void setY(int x){}
 public void setHomeX(int x){} public void setHomeY(int x){} public void setHeading(int x){}
 public void startTimedDelete(long ms) { if(ms!=60000L)throw new AssertionError("duration");if(failStart)throw new IllegalStateException("failed timed delete"); }
}''')
(inst/'TestNpcInstance.java').write_text('''package l1j.server.server.model.Instance;
import l1j.server.server.model.item.function.L1Npc;
public final class TestNpcInstance extends L1NpcInstance {
 public TestNpcInstance(L1Npc template) { if(template==null) throw new IllegalArgumentException(); }
}''')
stub='''package l1j.server.server.model.item.function;
import java.util.*;
import l1j.server.server.model.Instance.L1NpcInstance;
class L1ItemInstance {
 final int id; int count=1,charges=1;
 int recordingColumns;
 L1ItemInstance(int i,int c,int k){id=i;count=c;charges=k;}
 public int getRecordingColumns(){return recordingColumns;}
 public int getId(){return id;} public int getCount(){return count;} public int getChargeCount(){return charges;}
}
class Inventory {
 Map<Integer,L1ItemInstance> items=new HashMap<>();
 int saved;boolean failSave;
 L1ItemInstance getItem(int id){return items.get(id);}
 void saveItem(L1ItemInstance it,int flags){saved++;if(failSave)throw new IllegalStateException("dirty save failed");}
}
class L1PcInventory {static final int COL_SAVE_ALL=0x7fff;}
class GameMap { boolean recall=true;boolean isRecallPets(){return recall;} }
class L1PcInstance {
 final Inventory inv=new Inventory();final GameMap map=new GameMap();
 final Map<Integer,Object> pets=new HashMap<>();boolean war;
 Inventory getInventory(){return inv;} GameMap getMap(){return map;}
 boolean isInWarArea(){return war;} Map<Integer,Object> getPetList(){return pets;}
 int getId(){return 123;}int getMapId(){return 4;}int getX(){return 33000;}int getY(){return 32900;}
}
class L1InventoryTransaction implements AutoCloseable {
 static L1InventoryTransaction pending;
 final L1PcInstance pc;L1ItemInstance input;int stagedCharge=-1,stagedDelete=0;
 Runnable success,failure;boolean submitted;
 L1InventoryTransaction(L1PcInstance p){pc=p;pending=this;}
 boolean debit(L1ItemInstance it,int n){if(n<=0 || pc.inv.getItem(it.getId())!=it ||it.count<n)return false;
  input=it;stagedDelete=n;return true;}
 void changeChargeCount(L1ItemInstance it,int n){if(n<0||pc.inv.getItem(it.getId())!=it)throw new IllegalStateException("stale");input=it;stagedCharge=n;}
 void onFailure(Runnable r){failure=r;}
 void commit(boolean succeeds){if(!submitted)throw new AssertionError("not submitted");
  if(succeeds){if(stagedDelete>0){input.count-=stagedDelete;if(input.count==0)pc.inv.items.remove(input.id);}
    if(stagedCharge>=0)input.charges=stagedCharge;
    if(success!=null)success.run();
  }else if(failure!=null)failure.run();
 }
 public void close(){}
}
class User {static L1InventoryTransaction CheckAndSetPendingUseETCItem(L1PcInstance pc,int key){return new L1InventoryTransaction(pc);} }
class CacheD {static int accountOrderKey(String s,int id){return id;} }
class CDB {static void RequestUseItem(L1PcInstance pc,L1InventoryTransaction tx,Runnable cb){tx.submitted=true;tx.success=cb;} }
class NpcTable {
 static final NpcTable TABLE=new NpcTable();static boolean missing;
 static NpcTable getInstance(){return TABLE;}
 L1Npc getTemplate(int id){return missing?null:new L1Npc();}
}
class IdFactory {static IdFactory getInstance(){return new IdFactory();} int nextId(){return 2134;} }
class L1World {static L1World WORLD=new L1World(); int spawned;
 static L1World getInstance(){return WORLD;}
 void storeAndEnterWorld(L1NpcInstance npc){spawned++;}
}
class ActionCodes {static final int ACTION_Wand=15;}
class SC_SystemMessage {static int calls;static void send(L1PcInstance p,String msg){calls++;} }
class SC_AttackStatus {static boolean throwPacket;static int sent;
 static void sendAndBroadcast(L1PcInstance p,int a,int action,boolean b){sent++;if(throwPacket)throw new IllegalStateException("packet");}
}
class SC_ServerMessage {static int calls;static void send(L1PcInstance p,int code){calls++;} }
class CommonUtil {static int random(int n){if(n!=3)throw new AssertionError();return 1;} }
class ItemActionCompletion {static int finished;static void finishCommittedConsumable(L1PcInstance p,L1ItemInstance i){finished++;}}
class ItemReceiptOperations {
 static void UseDurableConsume(L1PcInstance pc,L1ItemInstance item,Runnable effect){
  if(pc.inv.getItem(item.id)!=item)return;
  try (L1InventoryTransaction tx=User.CheckAndSetPendingUseETCItem(pc,pc.getId())){
   if(!tx.debit(item,1))return;CDB.RequestUseItem(pc,tx,effect);
  }
 }
}
class L1SummonInstance {static int created;static boolean failSpawn;int petcost;
 L1SummonInstance(L1Npc npc,L1PcInstance pc){if(failSpawn)throw new IllegalStateException("spawn failed");created++;}
 void setPetcost(int c){petcost=c;if(c!=0)throw new AssertionError("petcost");}
}
class ItemActionContext {
 L1PcInstance pc;L1ItemInstance item;
 ItemActionContext(L1PcInstance p,L1ItemInstance i){pc=p;item=i;}
 L1PcInstance player(){return pc;} L1ItemInstance item(){return item;}
}
enum ItemActionResult {COMPLETE,STOP}
'''
(p/'Stubs.java').write_text(stub)
tests='''package l1j.server.server.model.item.function;
public final class Regression {
 static int checks;
 static void yes(boolean condition,String name){checks++;if(!condition)throw new AssertionError(name);}
 static L1PcInstance pc(L1ItemInstance it){L1PcInstance pc=new L1PcInstance();pc.inv.items.put(it.id,it);return pc;}
 static void reset(){L1InventoryTransaction.pending=null;ItemActionCompletion.finished=0;
   L1World.WORLD.spawned=0;SC_AttackStatus.sent=0;SC_AttackStatus.throwPacket=false;
   SC_SystemMessage.calls=0;SC_ServerMessage.calls=0;NpcTable.missing=false;L1SummonInstance.created=0;
  L1Npc.invalid=false;L1SummonInstance.failSpawn=false;
  l1j.server.server.model.Instance.L1NpcInstance.failStart=false;}
 static void wand(){
  reset();L1ItemInstance it=new L1ItemInstance(1,1,3);L1PcInstance p=pc(it);
  NpcWandAndSpawnItems.useNpcSpownWand(p,60001,it);
  yes(it.charges==3,"before commit charge");yes(L1World.WORLD.spawned==0,"before commit spawn");
  yes(ItemActionCompletion.finished==0,"before commit delay");
  L1InventoryTransaction.pending.commit(false);
  yes(it.charges==3,"rollback charge");yes(L1World.WORLD.spawned==0,"rollback spawn");
  yes(ItemActionCompletion.finished==0,"rollback delay");
  reset();it=new L1ItemInstance(2,1,3);p=pc(it);
  NpcWandAndSpawnItems.useNpcSpownWand(p,70030,it);L1InventoryTransaction.pending.commit(true);
  yes(it.charges==2,"commit charge");yes(L1World.WORLD.spawned==1,"commit spawn");
  yes(ItemActionCompletion.finished==1,"commit finish");yes(SC_AttackStatus.sent==1,"visual");
  reset();it=new L1ItemInstance(3,1,1);p=pc(it);
  NpcWandAndSpawnItems.useNpcSpownWand(p,60001,it);
  yes(p.inv.getItem(it.id)==it,"last charge precommit");
  L1InventoryTransaction.pending.commit(true);
  yes(p.inv.getItem(it.id)==null,"last charge deletion");yes(L1World.WORLD.spawned==1,"last charge spawn");
  yes(ItemActionCompletion.finished==1,"last charge finish");
  reset();it=new L1ItemInstance(4,1,2);p=pc(it);NpcTable.missing=true;
  NpcWandAndSpawnItems.useNpcSpownWand(p,60001,it);
  yes(L1InventoryTransaction.pending==null,"missing template no tx");
  reset();it=new L1ItemInstance(5,1,2);p=pc(it);p.inv.items.remove(it.id);
  NpcWandAndSpawnItems.useNpcSpownWand(p,60001,it);
  yes(L1InventoryTransaction.pending==null,"stale item no tx");
  reset();it=new L1ItemInstance(6,1,0);p=pc(it);
  NpcWandAndSpawnItems.useNpcSpownWand(p,60001,it);
  yes(L1InventoryTransaction.pending==null,"empty charge no tx");
  reset();it=new L1ItemInstance(7,1,3);p=pc(it);SC_AttackStatus.throwPacket=true;
  NpcWandAndSpawnItems.useNpcSpownWand(p,60001,it);L1InventoryTransaction.pending.commit(true);
  yes(it.charges==2,"packet failure charge persisted");
  yes(L1World.WORLD.spawned==1,"packet failure spawned");
  yes(ItemActionCompletion.finished==1,"packet failure finish");
  reset();it=new L1ItemInstance(8,1,3);p=pc(it);it.recordingColumns=8;
  NpcWandAndSpawnItems.useNpcSpownWand(p,60001,it);
  yes(p.inv.saved==1,"dirty preimage flushed before admission");
  yes(L1InventoryTransaction.pending!=null,"dirty transaction submitted");
  reset();it=new L1ItemInstance(9,1,3);p=pc(it);it.recordingColumns=8;p.inv.failSave=true;
  NpcWandAndSpawnItems.useNpcSpownWand(p,60001,it);
  yes(p.inv.saved==1,"dirty preflush attempted");
  yes(L1InventoryTransaction.pending==null,"dirty preflush fail no receipt");
  reset();it=new L1ItemInstance(10,1,2);p=pc(it);L1Npc.invalid=true;
  NpcWandAndSpawnItems.useNpcSpownWand(p,60001,it);
  yes(L1InventoryTransaction.pending==null,"invalid npc constructor no debit admission");
  reset();it=new L1ItemInstance(20,1,2);p=pc(it);
  l1j.server.server.model.Instance.L1NpcInstance.failStart=true;
  NpcWandAndSpawnItems.useNpcSpownWand(p,60001,it);
  boolean failed=false;
  try {L1InventoryTransaction.pending.commit(true);}catch(IllegalStateException e){failed=true;}
  yes(failed,"spawn failure propagated to quarantine path");
  yes(it.charges==1,"committed charge not rolled back by spawn failure");
  yes(ItemActionCompletion.finished==1,"spawn failure still finishes use exactly once");
 }
 static void summon(){
  reset();L1ItemInstance it=new L1ItemInstance(11,2,0);L1PcInstance p=pc(it);
  yes(PetAndSummonItems.handleSummonMonster(new ItemActionContext(p,it))==ItemActionResult.STOP,"return STOP");
  yes(L1SummonInstance.created==0,"summon before commit");yes(it.count==2,"count before commit");
  L1InventoryTransaction.pending.commit(false);
  yes(L1SummonInstance.created==0,"summon rollback");yes(it.count==2,"count rollback");
  yes(ItemActionCompletion.finished==0,"delay rollback");
  reset();it=new L1ItemInstance(12,1,0);p=pc(it);
  PetAndSummonItems.handleSummonMonster(new ItemActionContext(p,it));L1InventoryTransaction.pending.commit(true);
  yes(L1SummonInstance.created==1,"summon commit");yes(it.count==0,"count commit");
  yes(ItemActionCompletion.finished==1,"summon finish");
  reset();it=new L1ItemInstance(13,1,0);p=pc(it);p.map.recall=false;
  PetAndSummonItems.handleSummonMonster(new ItemActionContext(p,it));
  yes(L1InventoryTransaction.pending==null,"restricted map no tx");
  reset();it=new L1ItemInstance(14,1,0);p=pc(it);p.war=true;
  PetAndSummonItems.handleSummonMonster(new ItemActionContext(p,it));
  yes(L1InventoryTransaction.pending==null,"war no tx");
  reset();it=new L1ItemInstance(15,1,0);p=pc(it);p.pets.put(1,new Object());
  PetAndSummonItems.handleSummonMonster(new ItemActionContext(p,it));
  yes(L1InventoryTransaction.pending==null,"existing pet no tx");
  reset();it=new L1ItemInstance(16,1,0);p=pc(it);NpcTable.missing=true;
  PetAndSummonItems.handleSummonMonster(new ItemActionContext(p,it));
  yes(L1InventoryTransaction.pending==null,"missing summon template no tx");
  reset();it=new L1ItemInstance(17,1,0);p=pc(it);L1SummonInstance.failSpawn=true;
  PetAndSummonItems.handleSummonMonster(new ItemActionContext(p,it));
  boolean failed=false;
  try {L1InventoryTransaction.pending.commit(true);}catch(IllegalStateException e){failed=true;}
  yes(failed,"summon failure propagated to quarantine path");
  yes(it.count==0,"committed consumable not regranted on spawn failure");
  yes(ItemActionCompletion.finished==1,"summon failure still finishes use exactly once");
 }
 public static void main(String[]args){for(int i=0;i<100;i++){wand();summon();}
  System.out.println("Production method typed regression: checks="+checks+" PASS");}
}
'''
(p/'Regression.java').write_text(tests)
print(root)
