from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()/'src/l1j/server/server/model/item/function'
out=Path(sys.argv[2]).resolve();out.mkdir(parents=True,exist_ok=True)
def method(src,start):
 s=src.read_text();a=s.index(start);op=s.index('{',a);depth=1;i=op+1
 while depth:
  if s[i]=='{':depth+=1
  elif s[i]=='}':depth-=1
  i+=1
 return s[a:i]
pet=method(root/'PetWithdrawalItems.java','public static boolean canWithdrawPet(')
withdraw=method(root/'PetWithdrawalItems.java','public static boolean withdrawPet(')
flute=method(root/'PetAndSummonItems.java','static ItemActionResult handlePlayRaceFlute(')
(out/'PetWithdrawalItems.java').write_text('''package regression;
import java.util.*;
public final class PetWithdrawalItems {
'''+pet+'\n'+withdraw+'\n}')
(out/'PetAndSummonItems.java').write_text('''package regression;
import static regression.PetWithdrawalItems.withdrawPet;
public final class PetAndSummonItems {
'''+flute+'\n}')
(out/'Stubs.java').write_text('''package regression;
import java.util.*;
class L1ItemInstance {
 final int id,template;int count;
 L1ItemInstance(int id,int template,int count){this.id=id;this.template=template;this.count=count;}
 int getId(){return id;} int getItemId(){return template;} int getCount(){return count;}
}
class Inventory {
 final Map<Integer,L1ItemInstance> items=new HashMap<>();
 L1ItemInstance getItem(int id){return items.get(id);}
 L1ItemInstance findItemId(int template){for(L1ItemInstance i:items.values())if(i.template==template&&i.count>0)return i;return null;}
 void add(L1ItemInstance i){items.put(i.id,i);}
}
class MapData {boolean allow=true;boolean isTakePets(){return allow;}}
class Ability {int cha=18;int getTotalCha(){return cha;}}
class L1NpcInstance {int petcost=6;int getPetcost(){return petcost;}}
class L1PetInstance extends L1NpcInstance {
 final int amulet;static int spawned;static boolean fail;
 L1PetInstance(L1Npc npc,L1PcInstance pc,L1Pet p){if(fail)throw new IllegalStateException("spawn");amulet=p.amulet; spawned++;pc.pets.put(amulet,this);}
 int getItemObjId(){return amulet;}void setPetcost(int i){petcost=i;}
}
class L1PcInstance {
 final Inventory inv=new Inventory();final MapData map=new MapData();final Map<Integer,L1NpcInstance> pets=new HashMap<>();
 final Ability ability=new Ability();int mapId=4;boolean war;
 Inventory getInventory(){return inv;}MapData getMap(){return map;}int getMapId(){return mapId;}
 boolean isInWarArea(){return war;}Map<Integer,L1NpcInstance> getPetList(){return pets;}Ability getAbility(){return ability;}
 boolean isCrown(){return false;}boolean isElf(){return false;}boolean isWizard(){return false;}
 boolean isDarkelf(){return false;}boolean isDragonknight(){return false;}boolean isBlackwizard(){return false;}
}
class L1Pet {final int amulet,npcId;L1Pet(int a,int n){amulet=a;npcId=n;}int get_npcid(){return npcId;}}
class L1Npc {}
class PetTable {static final PetTable self=new PetTable();static boolean missing;static PetTable getInstance(){return self;}
 L1Pet getTemplate(int amulet){return missing?null:new L1Pet(amulet,1234);}
}
class NpcTable {static final NpcTable self=new NpcTable();static boolean missing;static NpcTable getInstance(){return self;}
 L1Npc getTemplate(int npc){return missing?null:new L1Npc();}
}
class SC_ServerMessage {static int sent;static void send(L1PcInstance pc,int id){sent++;}}
class ItemActionContext {
 final L1PcInstance pc;final L1ItemInstance item;final int id;
 ItemActionContext(L1PcInstance p,L1ItemInstance i,int id){pc=p;item=i;this.id=id;}
 L1PcInstance player(){return pc;}L1ItemInstance item(){return item;}int itemObjectId(){return id;}
}
enum ItemActionResult {COMPLETE,STOP}
class ItemActionCompletion {static int finished;static void finishCommittedConsumable(L1PcInstance pc,L1ItemInstance item){finished++;}}
class ItemReceiptOperations {
 static Runnable committed;static L1ItemInstance pending;static L1PcInstance owner;static int submitted;
 static void UseDurableConsume(L1PcInstance pc,L1ItemInstance input,Runnable effect){
  if(pc.inv.getItem(input.id)!=input||input.count==0)return;
  submitted++;committed=effect;pending=input;owner=pc;
 }
 static void commit(boolean ok){if(committed==null)throw new AssertionError("no receipt");Runnable c=committed;committed=null;
  if(ok){pending.count--;if(pending.count==0)owner.inv.items.remove(pending.id);c.run();}}
}
''')
(out/'Regression.java').write_text('''package regression;
public final class Regression {
 static int checks; static void yes(boolean b,String what){checks++;if(!b)throw new AssertionError(what);}
 static void reset(){PetTable.missing=false;NpcTable.missing=false;L1PetInstance.spawned=0;L1PetInstance.fail=false;
 SC_ServerMessage.sent=0;ItemActionCompletion.finished=0;ItemReceiptOperations.submitted=0;
 ItemReceiptOperations.committed=null;ItemReceiptOperations.pending=null;}
 static L1PcInstance setup(){L1PcInstance pc=new L1PcInstance();pc.inv.add(new L1ItemInstance(1,40314,1));
 pc.inv.add(new L1ItemInstance(2,41160,2));return pc;}
 static void test(){
 reset();L1PcInstance p=setup();L1ItemInstance amulet=p.inv.getItem(1),flute=p.inv.getItem(2);
 yes(PetAndSummonItems.handlePlayRaceFlute(new ItemActionContext(p,amulet,1))==ItemActionResult.STOP,"async gate");
 yes(L1PetInstance.spawned==0&&flute.count==2&&ItemActionCompletion.finished==0,"pre commit");
 ItemReceiptOperations.commit(false);
 yes(L1PetInstance.spawned==0&&flute.count==2&&ItemActionCompletion.finished==0,"rollback");
 reset();p=setup();amulet=p.inv.getItem(1);flute=p.inv.getItem(2);
 PetAndSummonItems.handlePlayRaceFlute(new ItemActionContext(p,amulet,1));ItemReceiptOperations.commit(true);
 yes(flute.count==1&&L1PetInstance.spawned==1&&ItemActionCompletion.finished==1,"commit exactly once");
 reset();p=setup();amulet=p.inv.getItem(1);flute=p.inv.getItem(2);p.inv.items.remove(flute.id);
 PetAndSummonItems.handlePlayRaceFlute(new ItemActionContext(p,amulet,1));
 yes(ItemReceiptOperations.submitted==0&&L1PetInstance.spawned==0&&ItemActionCompletion.finished==0,"missing flute");
 reset();p=setup();amulet=p.inv.getItem(1);p.map.allow=false;
 PetAndSummonItems.handlePlayRaceFlute(new ItemActionContext(p,amulet,1));
 yes(ItemReceiptOperations.submitted==0&&L1PetInstance.spawned==0,"map rejected");
 reset();p=setup();amulet=p.inv.getItem(1);p.war=true;
 PetAndSummonItems.handlePlayRaceFlute(new ItemActionContext(p,amulet,1));
 yes(ItemReceiptOperations.submitted==0&&L1PetInstance.spawned==0,"war rejected");
 reset();p=setup();amulet=p.inv.getItem(1);p.pets.put(1,new L1PetInstance(new L1Npc(),new L1PcInstance(),new L1Pet(1,1234)));
 PetAndSummonItems.handlePlayRaceFlute(new ItemActionContext(p,amulet,1));
 yes(ItemReceiptOperations.submitted==0,"duplicate pet preflight");
 reset();p=setup();amulet=p.inv.getItem(1);NpcTable.missing=true;
 PetAndSummonItems.handlePlayRaceFlute(new ItemActionContext(p,amulet,1));yes(ItemReceiptOperations.submitted==0,"missing npc");
 reset();p=setup();amulet=p.inv.getItem(1);p.ability.cha=0;
 PetAndSummonItems.handlePlayRaceFlute(new ItemActionContext(p,amulet,1));yes(ItemReceiptOperations.submitted==0,"charisma");
 reset();p=setup();amulet=p.inv.getItem(1);p.inv.items.remove(1);
 PetAndSummonItems.handlePlayRaceFlute(new ItemActionContext(p,amulet,1));yes(ItemReceiptOperations.submitted==0,"amulet not owned");
 reset();p=setup();amulet=p.inv.getItem(1);p.inv.add(new L1ItemInstance(3,40316,1));
 PetAndSummonItems.handlePlayRaceFlute(new ItemActionContext(p,amulet,3));
 yes(ItemReceiptOperations.submitted==0,"amulet object id mismatch");
 reset();p=setup();amulet=p.inv.getItem(1);PetTable.missing=true;
 PetAndSummonItems.handlePlayRaceFlute(new ItemActionContext(p,amulet,1));yes(ItemReceiptOperations.submitted==0,"missing pet row");
 reset();p=setup();amulet=p.inv.getItem(1);
 PetAndSummonItems.handlePlayRaceFlute(new ItemActionContext(p,amulet,1));p.pets.put(1,new L1PetInstance(new L1Npc(),new L1PcInstance(),new L1Pet(1,1234)));L1PetInstance.spawned=0;
 ItemReceiptOperations.commit(true);
 yes(L1PetInstance.spawned==0&&ItemActionCompletion.finished==1,"preimage revalidation after commit");
 reset();p=setup();amulet=p.inv.getItem(1);
 PetAndSummonItems.handlePlayRaceFlute(new ItemActionContext(p,amulet,1));
 p.inv.items.put(1,new L1ItemInstance(1,40314,1));ItemReceiptOperations.commit(true);
 yes(L1PetInstance.spawned==0&&ItemActionCompletion.finished==1,"replacement amulet after commit no spawn");
 reset();p=setup();amulet=p.inv.getItem(1);L1PetInstance.fail=true;
 PetAndSummonItems.handlePlayRaceFlute(new ItemActionContext(p,amulet,1));
 boolean thrown=false;try{ItemReceiptOperations.commit(true);}catch(IllegalStateException ex){thrown=true;}
 yes(thrown&&ItemActionCompletion.finished==1,"world failure surfaced; committed debit remains");
 }
 public static void main(String[]args){for(int i=0;i<200;i++)test();System.out.println("Pet flute production-method tests: "+checks+" PASS");}
}
''')
