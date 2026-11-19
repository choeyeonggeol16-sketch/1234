import java.util.*;
class L1Inventory {static final int OK=0;}
class L1PcInventory extends L1Inventory {
 static final int COL_BLESS=9;
 final Map<Integer,L1ItemInstance> owned=new LinkedHashMap<>();
 int checks,stores,updates,saves; boolean reject,failCheck,failStore,storeNull,failUpdate,failSave;
 int checkAddItem(L1ItemInstance it,int count){checks++;if(failCheck)throw new IllegalStateException("check");return reject?1:OK;}
 L1ItemInstance getItem(int id){return owned.get(id);}
 L1ItemInstance storeItem(L1ItemInstance it){stores++;if(failStore)throw new IllegalStateException("store");if(storeNull)return null;
  if(it.itemId==101)for(L1ItemInstance v:owned.values())if(v.itemId==it.itemId){v.count+=it.count;return v;}
  owned.put(it.id,it);return it;
 }
 void updateItem(L1ItemInstance it,int column){updates++;if(failUpdate)throw new IllegalStateException("update");Trace.events.add("update:"+it.id+":"+it.bless);}
 void saveItem(L1ItemInstance it,int column){saves++;if(failSave)throw new IllegalStateException("save");Trace.events.add("save:"+it.id+":"+it.bless);}
}
class L1ItemInstance {final int id,itemId;int count=1,enchant,attr,bless;boolean identified=true;
 L1ItemInstance(int id,int itemId){this.id=id;this.itemId=itemId;}
 int getId(){return id;}void setCount(int n){count=n;}void setIdentified(boolean b){identified=b;}
 void setEnchantLevel(int v){enchant=v;}void setAttrEnchantLevel(int v){attr=v;}void setBless(int v){bless=v;}
 String getLogName(){return itemId+"*"+count+" +"+enchant;}
}
class L1PcInstance {final L1PcInventory inventory=new L1PcInventory();L1PcInventory getInventory(){return inventory;}}
class L1World {static final L1World INSTANCE=new L1World();static L1World getInstance(){return INSTANCE;}final Map<Integer,L1ItemInstance> all=new HashMap<>();
 void storeObject(L1ItemInstance item){all.put(item.id,item);}void removeObject(L1ItemInstance item){all.remove(item.id,item);}}
class ItemTable {static final ItemTable INSTANCE=new ItemTable();static ItemTable getInstance(){return INSTANCE;}static int next=1000;
 L1ItemInstance createItem(int id){L1ItemInstance it=new L1ItemInstance(next++,id);L1World.getInstance().storeObject(it);return it;}}
class Trace {static final List<String> events=new ArrayList<>();}
class SC_ServerMessage {static void send(L1PcInstance pc,int code,String... args){Trace.events.add("message:"+code+":"+Arrays.toString(args));}}
