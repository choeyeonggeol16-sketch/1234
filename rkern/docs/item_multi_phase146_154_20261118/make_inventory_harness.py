from pathlib import Path
import sys
ROOT=Path(sys.argv[1]); OUT=Path(sys.argv[2]); OUT.mkdir(parents=True,exist_ok=True)
p=ROOT/'src/l1j/server/server/model/CUserInventory.java' 
s=p.read_text()
a=s.index('    void applyEconomyImage(')
openb=s.index('{',a)
level=1
k=openb+1
while level:
 if s[k]=='{':level+=1
 if s[k]=='}':level-=1
 k+=1
method=s[a:k]
assert 'receiptNoticeFailed("item-status"' in method
h='''import java.util.*;
import java.util.logging.*;

public class ReplayHarness {
    private static int tests;
    static void check(boolean ok, String msg) {tests++; if (!ok) throw new AssertionError(msg);}
    static L1ItemInstance item(int id,int template,int count,int ench) {
        L1ItemInstance x=new L1ItemInstance(id,template,count);x.ench=ench;return x;
    }
    static CUserInventory initial(L1ItemInstance x) {
        CUserInventory inv=new CUserInventory();
        inv._items.add(x);inv.addItemCountCache(x.getItemId(),x.getCount());
        L1World.getInstance().storeObject(x);return inv;
    }
    static void clear() {Notice.clear();L1World.getInstance().objects.clear();}
    static void testUpdate(String failed) {
        clear();L1ItemInstance original=item(10,20,4,0);
        CUserInventory inv=initial(original);L1ItemInstance image=item(10,20,2,1);
        Notice.fail=failed;inv.applyEconomyImage(original,image);
        check(original.getCount()==2,"update not applied: "+failed);
        check(inv.getItem(10)==original,"object identity changed");
        check(inv._itemCountMap.get(20)==2,"count cache wrong: "+failed);
        if (!failed.equals("item-color")) check(Notice.sent.contains("item-color"),"later status not sent");
        if (!failed.equals("weight")) check(Notice.sent.contains("weight"),"weight omitted");
    }
    static void testDelete(String failed) {
        clear();L1ItemInstance original=item(11,900075,1,0);
        CUserInventory inv=initial(original); inv._owner.poly=true;
        Notice.fail=failed;inv.applyEconomyImage(original,null);
        check(inv.getItem(11)==null,"delete not applied: "+failed);
        check(!inv._owner.poly,"ability state not reset: "+failed);
        check(L1World.getInstance().findObject(11)==null,"world item remains");
        check(!inv._itemCountMap.containsKey(900075),"count cache remains");
        if (!failed.equals("weight"))check(Notice.sent.contains("weight"),"weight omitted on delete");
    }
    static void testGrant(String failed) {
        clear();CUserInventory inv=new CUserInventory();
        L1ItemInstance image=item(12,900075,1,2);
        Notice.fail=failed;inv.applyEconomyImage(null,image);
        check(inv.getItem(12)==image,"grant not applied: "+failed);
        check(inv._itemCountMap.get(900075)==1,"grant count cache wrong");
        check(inv._owner.poly,"ability not set: "+failed);
        if (!failed.equals("weight"))check(Notice.sent.contains("weight"),"weight omitted on grant");
    }
    public static void main(String[] args) {
        for(String name:new String[]{"item-status","item-name","item-color","weight"})testUpdate(name);
        for(String name:new String[]{"delete","ability-off-2","ability-off-7","weight"})testDelete(name);
        for(String name:new String[]{"item-added","ability-on-2","ability-on-7","new-item-status","new-item-enchant","weight"})testGrant(name);
        clear();CUserInventory inv=new CUserInventory();
        try {inv.applyEconomyImage(item(5,2,1,0),null);throw new AssertionError("stale object accepted");}
        catch (IllegalStateException correct) {tests++;}
        l1j.server.server.storage.CacheD.applying=false;
        try {inv.applyEconomyImage(null,item(6,2,1,0));throw new AssertionError("receipt scope ignored");}
        catch (IllegalStateException correct) {tests++;}
        System.out.println("PRODUCTION_INVENTORY_REPLAY_TESTS="+tests+" PASS");
    }
}

class Notice {
 static String fail="";static final Set<String> sent=new HashSet<>();
 static void clear(){fail="";sent.clear();}
 static void send(String name){sent.add(name);if(name.equals(fail))throw new IllegalStateException("injected packet failure: "+name);}
}
class CUserInventory {
 static final Logger RECEIPT_NOTICE_LOG=Logger.getLogger(CUserInventory.class.getName());
 static void receiptNoticeFailed(String name,RuntimeException failure){}
 final L1PcInstance _owner=new L1PcInstance();
 final List<L1ItemInstance> _items=new ArrayList<>();
 final Map<Integer,Integer> _itemCountMap=new HashMap<>();
 final Map<Integer,Integer> _equippedCountMap=new HashMap<>();
 L1ItemInstance getItem(int id){for(L1ItemInstance x:_items)if(x.id==id)return x;return null;}
 void removeItemCountCache(int id,int n){int next=_itemCountMap.getOrDefault(id,0)-n;if(next>0)_itemCountMap.put(id,next);else _itemCountMap.remove(id);}
 void addItemCountCache(int id,int n){_itemCountMap.put(id,_itemCountMap.getOrDefault(id,0)+n);}
 void notifyEconomyBookmark(L1ItemInstance item){}
'''+method+'''
}
class L1ItemInstance {
 int id,itemId,count,ench,durability,charge,remaining,lastUsed,bless,attr,special,pkg,blessLevel,blessLevel2,itemLevel,hotel;
 boolean identified,equipped;java.util.Date endTime;ItemDef def;
 L1PcInstance _cha;LastStatus status=new LastStatus();
 L1ItemInstance(int id,int itemId,int count){this.id=id;this.itemId=itemId;this.count=count;def=new ItemDef(itemId);}
 int getId(){return id;}int getItemId(){return def.itemId;}ItemDef getItem(){return def;}
 int getCount(){return count;}void setCount(int n){count=n;}
 int getEnchantLevel(){return ench;}void setEnchantLevel(int n){ench=n;}
 boolean isIdentified(){return identified;}void setIdentified(boolean x){identified=x;}
 int get_durability(){return durability;}void set_durability(int x){durability=x;}
 int getChargeCount(){return charge;}void setChargeCount(int x){charge=x;}
 int getRemainingTime(){return remaining;}void setRemainingTime(int x){remaining=x;}
 int getLastUsed(){return lastUsed;}void setLastUsed(int x){lastUsed=x;}
 int getBless(){return bless;}void setBless(int x){bless=x;}
 int getAttrEnchantLevel(){return attr;}void setAttrEnchantLevel(int x){attr=x;}
 int getSpecialEnchant(){return special;}void setSpecialEnchant(int x){special=x;}
 boolean isPackage(){return pkg!=0;}void setPackage(boolean x){pkg=x?1:0;}
 int get_bless_level(){return blessLevel;}void set_bless_level(int x){blessLevel=x;}
 int get_bless_level2(){return blessLevel2;}void set_bless_level2(int x){blessLevel2=x;}
 int get_item_level(){return itemLevel;}void set_item_level(int x){itemLevel=x;}
 int getHotel_Town(){return hotel;}void setHotel_Town(int x){hotel=x;}
 java.util.Date getEndTime(){return endTime;}void setEndTime(java.util.Date x){endTime=x;}
 boolean isEquipped(){return equipped;}void setItem(ItemDef x){def=x;}
 LastStatus getLastStatus(){return status;}
}
class ItemDef {int itemId;ItemDef(int id){itemId=id;}int getItemId(){return itemId;} boolean isEndedTimeMessage(){return false;}}
class LastStatus {void updateAll(){}}
class L1PcInstance {boolean poly;int getId(){return 42;}boolean isLogoutStarted(){return false;}EquipSlot getEquipSlot(){return new EquipSlot();}
 void setCurrentHp(int n){}int getCurrentHp(){return 100;}void setCurrentMp(int n){}int getCurrentMp(){return 100;}
 void setPolyRingMaster(boolean n){poly=n;}boolean hasSkillEffect(int n){return false;}void removeSkillEffect(int n){}
 boolean isInParty(){return false;}Party getParty(){return new Party();}}
class EquipSlot {void remove(L1ItemInstance i){}void set(L1ItemInstance i){}}
class Party {void refreshPartyMemberStatus(L1PcInstance pc){}}
class L1PolyMorph {static void undoPoly(L1PcInstance pc){}}
class L1SkillId {static final int POLY_RING_MASTER=1;}
class L1World {static final L1World W=new L1World();final Map<Integer,L1ItemInstance> objects=new HashMap<>();
 static L1World getInstance(){return W;}void removeObject(L1ItemInstance i){objects.remove(i.id);}
 void storeObject(L1ItemInstance i){objects.put(i.id,i);}L1ItemInstance findObject(int id){return objects.get(id);}}
class SC_DeleteInventoryItem {static void send(L1PcInstance pc,L1ItemInstance item){Notice.send("delete");}}
class SC_ItemStatus {static void send(L1PcInstance pc,L1ItemInstance item){Notice.send(item==null?"":"item-status");}}
class SC_ItemName {static void send(L1PcInstance pc,L1ItemInstance item){Notice.send("item-name");}}
class SC_ItemColor {static void send(L1PcInstance pc,L1ItemInstance item){Notice.send("item-color");}}
class SC_Weight {static void send(L1PcInstance pc){Notice.send("weight");}}
class SC_Ability {static void send(L1PcInstance pc,int type,boolean enabled){Notice.send("ability-"+(enabled?"on":"off")+"-"+type);}}
class SC_ADD_INVENTORY_NOTI {static void sendItem(L1PcInstance pc,L1ItemInstance item,boolean v,int id){Notice.send("item-added");}}
class SC_PacketBox {static final int ITEM_ENCHANT_UPDATE=1;static void send(L1PcInstance pc,int id,L1ItemInstance item){Notice.send("new-item-enchant");}}
'''
# Replace item status call for new items with distinct marker for test clarity, not behavior.
h=h.replace('class SC_ItemStatus {static void send(L1PcInstance pc,L1ItemInstance item){Notice.send(item==null?"":"item-status");}}',
'''class SC_ItemStatus {static void send(L1PcInstance pc,L1ItemInstance item){Notice.send(item.id==12?"new-item-status":"item-status");}}''')
(OUT/'ReplayHarness.java').write_text(h)
cs='''package l1j.server.server.storage;
public class CacheD { public static boolean applying=true; public static boolean isWarehouseApplying(){return applying;} }
'''
q=(OUT/'l1j/server/server/storage/CacheD.java');q.parent.mkdir(parents=True,exist_ok=True);q.write_text(cs)
q=(OUT/'l1j/server/server/model/character/scheduler/PcInventoryExpiryScheduler.java');q.parent.mkdir(parents=True,exist_ok=True)
# Ref arguments are in the default package; cannot import from a named package. Use Object.
q.write_text('''package l1j.server.server.model.character.scheduler;
public class PcInventoryExpiryScheduler {
 static final PcInventoryExpiryScheduler I=new PcInventoryExpiryScheduler();
 public static PcInventoryExpiryScheduler getInstance(){return I;}
 public void cancelItem(Object p,Object i){}public void scheduleItem(Object p,Object i){}
}
''')
print('wrote',len(h))
