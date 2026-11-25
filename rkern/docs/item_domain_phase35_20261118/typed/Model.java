import java.util.*;
final class TestRng {static long seed;}
final class Trace {static final List<String> events=new ArrayList<>(); static void clear(){events.clear();}}
final class CInventory {static final int OK=0;}
final class L1World {
 static final L1World INSTANCE=new L1World();
 final Map<Integer,L1ItemInstance> objects=new HashMap<>();
 static L1World getInstance(){return INSTANCE;}
 void storeObject(L1ItemInstance item){objects.put(item.getId(),item);}
 void removeObject(L1ItemInstance item){objects.remove(item.getId(),item);}
}
final class L1ItemInstance {
 int id,itemId,count=1;boolean identified=true;
 L1ItemInstance(int id,int itemId){this.id=id;this.itemId=itemId;}
 int getId(){return id;}
 void setCount(int c){count=c;}
 String getLogName(){return getNumberedName(count);}
 String getNumberedName(int count){return "item:"+itemId+" *"+count+" identified:"+identified;}
}
final class ItemTable {
 static final ItemTable INSTANCE=new ItemTable();static int next=1000,created;
 static ItemTable getInstance(){return INSTANCE;}
 L1ItemInstance createItem(int id){if(id<=0)return null;L1ItemInstance it=new L1ItemInstance(next++,id);L1World.getInstance().storeObject(it);created++;return it;}
}
final class CUserInventory {
 boolean reject,failCheck,failStore,returnNull;
 int checks,stores; final Map<Integer,L1ItemInstance> byId=new HashMap<>();
 int checkAddItem(L1ItemInstance it,int count){checks++;if(failCheck)throw new IllegalStateException("check");return reject?1:CInventory.OK;}
 L1ItemInstance getItem(int id){return byId.get(id);}
 void addExisting(L1ItemInstance it){byId.put(it.id,it);L1World.getInstance().storeObject(it);}
 L1ItemInstance storeItem(L1ItemInstance it){stores++;if(failStore)throw new IllegalStateException("store");if(returnNull)return null;
  for(L1ItemInstance value:byId.values())if(value.itemId==it.itemId){value.count+=it.count;return value;}
  byId.put(it.id,it);return it;
 }
 L1ItemInstance storeItem(int id,int count){stores++;if(failStore)throw new IllegalStateException("store");if(returnNull)return null;
  for(L1ItemInstance value:byId.values())if(value.itemId==id){value.count+=count;return value;}
  L1ItemInstance it=ItemTable.getInstance().createItem(id);it.count=count;byId.put(it.id,it);return it;
 }
}
final class L1PcInstance {final CUserInventory inv=new CUserInventory();CUserInventory getInventory(){return inv;}int getMapId(){return 1;}String getName(){return "player";}}
final class SC_ServerMessage {
 static void send(L1PcInstance pc,int code,String... values){Trace.events.add("message:"+code+":"+Arrays.toString(values));}
}
final class SC_SystemMessage {static void send(L1PcInstance pc,String text){Trace.events.add("system:"+text);}}
