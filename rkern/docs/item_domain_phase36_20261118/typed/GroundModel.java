import java.util.*;
final class L1Item {final int itemId; final boolean stackable;L1Item(int i,boolean st){itemId=i;stackable=st;}boolean isStackable(){return stackable;}}
final class L1ItemInstance {final int id,itemId;int count=1,enchant;L1ItemInstance(int i,int p){id=i;itemId=p;}int getId(){return id;}void setEnchantLevel(int x){enchant=x;}void setCount(int x){count=x;}}
final class L1Location {
 final int x,y;L1Location(int x,int y){this.x=x;this.y=y;}
 L1Location randomLocation(int radius,boolean b){return new L1Location(x,y);}
 int getX(){return x;}int getY(){return y;}
}
class L1Inventory {static final int OK=0;}
final class L1World {
 static final L1World INSTANCE=new L1World();static L1World getInstance(){return INSTANCE;}
 final Map<Integer,L1ItemInstance> world=new HashMap<>();final L1GroundInventory ground=new L1GroundInventory();
 void storeObject(L1ItemInstance item){world.put(item.id,item);}void removeObject(L1ItemInstance item){world.remove(item.id,item);}
 L1GroundInventory getInventory(int x,int y,short m){return ground;}
 void reset(){world.clear();ground.reset();}
}
final class L1GroundInventory extends L1Inventory {
 boolean reject,failCheck,failStore,returnNull;int checkCalls,storeCalls;final Map<Integer,L1ItemInstance> owned=new LinkedHashMap<>();
 void reset(){reject=failCheck=failStore=returnNull=false;checkCalls=storeCalls=0;owned.clear();}
 int checkAddItem(L1ItemInstance it,int count){checkCalls++;if(failCheck)throw new IllegalStateException("check");return reject?1:OK;}
 L1ItemInstance getItem(int id){return owned.get(id);}
 L1ItemInstance storeItem(L1ItemInstance item){storeCalls++;if(failStore)throw new IllegalStateException("store");if(returnNull)return null;
  if(item.itemId==100)for(L1ItemInstance found:owned.values())if(found.itemId==item.itemId){found.count+=item.count;return found;}
  owned.put(item.id,item);return item;
 }
}
final class ItemTable {
 static final ItemTable INSTANCE=new ItemTable();static ItemTable getInstance(){return INSTANCE;}static int next=1000;
 L1Item getTemplate(int id){return id==100?new L1Item(id,true):id==101?new L1Item(id,false):null;}
 L1ItemInstance createItem(int id){L1ItemInstance item=new L1ItemInstance(next++,id);L1World.getInstance().storeObject(item);return item;}
}
