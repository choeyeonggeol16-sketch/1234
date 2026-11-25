package l1j.server.server.model;
import java.util.HashMap;
import java.util.Map;
import l1j.server.server.model.Instance.L1ItemInstance;
public class CUserInventory {
 public static final int COL_BLESS=1;
 public int reject=0;public boolean failCheck=false,failStore=false,returnNull=false,stack=false;
 public int saveCalls,updateCalls; public int checkCalls,storeCalls;
 public final Map<Integer,L1ItemInstance> entries=new HashMap<>();
 public int checkAddItem(L1ItemInstance item,int count){checkCalls++;if(failCheck)throw new IllegalStateException("check fail");return reject;}
 public L1ItemInstance storeItem(L1ItemInstance item){storeCalls++;if(failStore)throw new IllegalStateException("store fail");if(returnNull)return null;
   if(stack){for(L1ItemInstance x:entries.values())if(x.getItemId()==item.getItemId()){x.setCount(x.getCount()+item.getCount());return x;}}
   entries.put(item.getId(),item);return item;}
 public L1ItemInstance getItem(int obj){return entries.get(obj);}
 public void updateItem(L1ItemInstance item,int mask){updateCalls++;}
 public void saveItem(L1ItemInstance item,int mask){saveCalls++;}
}