package l1j.server.server.model;
import java.util.*;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.Instance.L1ItemInstance;
public class L1PcInventory {
 public boolean reject, throwCheck,throwStore,returnNull, lateOwnership;
 public int checkCalls,storeCalls,checkOwnedCalls;
 public final Map<Integer,L1ItemInstance> owned=new HashMap<>();
 public int checkAddItem(L1ItemInstance item,int count){checkCalls++;if(throwCheck)throw new IllegalStateException("check fail");return reject?1:OK;}
 private static final int OK=L1Inventory.OK;
 public boolean checkItem(int id){checkOwnedCalls++;for(var item:owned.values())if(item.getItem().itemId==id)return true;return false;}
 public L1ItemInstance getItem(int id){return owned.get(id);}
 public L1ItemInstance storeItem(L1ItemInstance item){storeCalls++;
  if(throwStore)throw new IllegalStateException("store fail");
  if(returnNull)return null;
  if(item.getItem().isStackable())for(var existing:owned.values())if(existing.getItem().itemId==item.getItem().itemId){existing.setCount(existing.getCount()+item.getCount());return existing;}
  owned.put(item.getId(),item);return item;
 }
 public L1ItemInstance storeItem(int id,int count){L1ItemInstance item=ItemTable.getInstance().createItem(id);if(item==null)return null;
  item.setCount(count);try{return storeItem(item);}finally{if(getItem(item.getId())!=item)L1World.getInstance().removeObject(item);}
 }
 public void putExisting(L1ItemInstance item){owned.put(item.getId(),item);L1World.getInstance().storeObject(item);}
 public int total(int id){int n=0;for(var v:owned.values())if(v.getItem().itemId==id)n+=v.getCount();return n;}
}