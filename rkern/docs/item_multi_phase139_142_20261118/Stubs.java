package l1j.server.server.model.item.function;

import java.util.*;
import java.util.logging.*;

class ItemActionResult {
    static final ItemActionResult STOP=new ItemActionResult();
    static final ItemActionResult COMPLETE=new ItemActionResult();
}
class ItemActionContext {
    final L1PcInstance pc; final L1ItemInstance item,target; final int itemId;
    ItemActionContext(L1PcInstance pc,L1ItemInstance item,L1ItemInstance target,int itemId){
        this.pc=pc;this.item=item;this.target=target;this.itemId=itemId;
    }
    L1PcInstance player(){return pc;} L1ItemInstance item(){return item;}
    L1ItemInstance targetItem(){return target;} int itemId(){return itemId;}
}
class L1ItemInstance {
    final int id,itemId; final MockItem template; int durability=10,charge=0; boolean equipped;
    L1ItemInstance(int id,int itemId){this.id=id;this.itemId=itemId;template=new MockItem(itemId);}
    int getId(){return id;} int getItemId(){return itemId;} String getName(){return "item"+itemId;}
    String getLogName(){return getName();} MockItem getItem(){return template;}
    int get_durability(){return durability;}boolean isEquipped(){return equipped;}
    int getChargeCount(){return charge;}
}
class MockItem { final int id; int type2=2; MockItem(int id){this.id=id;}int getType2(){return type2;} }
class Inventory {
    Map<Integer,L1ItemInstance> items=new HashMap<>();
    L1ItemInstance getItem(int id){return items.get(id);} }
class AC { int sum;void addAc(int delta){sum+=delta;} }
class L1PcInstance {
    Inventory inv=new Inventory(); int id=17,mp=10;long quizTime;AC ac=new AC();
    int notices,skills;boolean failPacket;
    int getId(){return id;} Inventory getInventory(){return inv;}
    long getQuizTime(){return quizTime;}int getCurrentMp(){return mp;}
    void setCurrentMp(int n){mp=n;}AC getAC(){return ac;}
    void sendPackets(String s){notices++;if(failPacket) throw new IllegalStateException("message");}
}
class ItemActionCompletion {
    static int completions=0;
    static void finishCommittedConsumable(L1PcInstance pc,L1ItemInstance item){completions++;}
}
class ItemReceiptOperations {
    static Runnable callback;static int submits;static boolean succeed=true;
    static void submit(Runnable r){submits++;if(succeed)callback=r;}
    static void UseDurableTwoInput(L1PcInstance pc,L1ItemInstance x,L1ItemInstance y,int reward,Runnable r){submit(r);}
    static void UseDurableTwoInput(L1PcInstance pc,L1ItemInstance x,L1ItemInstance y,int reward,int cnt,Runnable r){submit(r);}
    static void UseDurableConsumeTwoInput(L1PcInstance pc,L1ItemInstance x,L1ItemInstance y,Runnable r){submit(r);}
    static void UseDurableConsume(L1PcInstance pc,L1ItemInstance x,Runnable r){submit(r);}
}
class DurabilityRepairItems {
    static void UseDurableRepair(L1PcInstance pc,L1ItemInstance a,L1ItemInstance b,Runnable r){ItemReceiptOperations.submit(r);}
}
class SpecialEnchantScrolls {
    static boolean isOwnedScrollTarget(L1PcInstance pc,L1ItemInstance a,L1ItemInstance b){
       return a!=null&&b!=null&&a!=b&&pc.getInventory().getItem(a.getId())==a&&pc.getInventory().getItem(b.getId())==b;
    }
}
class CreatureItemActions {
    static final Random _random=new Random(1);
    static final Logger ITEM_USE2_LOG=Logger.getLogger("typed-test");
}
class Config {static int CREATE_CHANCE_PROCESSING=200,CREATE_CHANCE_DANTES=200, 방어구비법서확률=200;}
class CommonUtil {static int random(int n){return 0;}}
class SC_ServerMessage {static int count;static boolean fail;static void send(L1PcInstance pc, Object... x){count++;if(fail) throw new IllegalStateException("packet");}}
class SC_SystemMessage {static int count;static boolean fail;static void send(L1PcInstance pc,String s){count++;if(fail) throw new IllegalStateException("packet");}}
class SC_ChatPacket {static void sendDirect(L1PcInstance pc,L1PcInstance target,String s){}}
class SC_SkillSound {static void send(L1PcInstance pc,int target,int code){}}
class SC_OwnCharAttrDef {static void send(L1PcInstance pc){}}
