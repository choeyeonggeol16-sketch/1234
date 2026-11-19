package l1j.server.server.datatables;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.L1World;
public class ItemTable {
 private static final ItemTable INSTANCE=new ItemTable();private int next=100;
 public static ItemTable getInstance(){return INSTANCE;}
 public L1ItemInstance createItem(int template){if(template<1)return null;
  L1ItemInstance v=new L1ItemInstance(next++,template);L1World.getInstance().storeObject(v);return v;}
}