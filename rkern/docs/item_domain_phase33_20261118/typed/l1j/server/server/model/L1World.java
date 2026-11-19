package l1j.server.server.model;
import java.util.HashMap;import java.util.Map;
import l1j.server.server.model.Instance.L1ItemInstance;
public final class L1World {
 private static final L1World INSTANCE=new L1World();
 private final Map<Integer,L1ItemInstance> entries=new HashMap<>();
 public static L1World getInstance(){return INSTANCE;}
 public void storeObject(L1ItemInstance item){entries.put(item.getId(),item);}
 public void removeObject(L1ItemInstance item){if(entries.get(item.getId())==item)entries.remove(item.getId());}
 public int size(){return entries.size();}
 public L1ItemInstance find(int id){return entries.get(id);}
 public void clear(){entries.clear();}
}