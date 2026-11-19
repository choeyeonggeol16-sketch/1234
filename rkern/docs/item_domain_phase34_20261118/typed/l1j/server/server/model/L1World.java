package l1j.server.server.model;
import java.util.*;
import l1j.server.server.model.Instance.L1ItemInstance;
public class L1World {
 static final L1World instance=new L1World();
 private final Map<Integer,L1ItemInstance> world=new HashMap<>();
 public static L1World getInstance(){return instance;}
 public void storeObject(L1ItemInstance item){world.put(item.getId(),item);}
 public void removeObject(L1ItemInstance item){world.remove(item.getId(),item);}
 public int size(){return world.size();}
 public void clear(){world.clear();}
}