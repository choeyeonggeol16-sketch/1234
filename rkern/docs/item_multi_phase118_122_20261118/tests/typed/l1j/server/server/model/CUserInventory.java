package l1j.server.server.model;
import l1j.server.server.model.Instance.L1ItemInstance;
public class CUserInventory {public static final int COL_DELAY_EFFECT=1,COL_CHARGE_COUNT=2;
 public L1ItemInstance item;public int saves;
 public L1ItemInstance getItem(int id){return item!=null&&item.getId()==id&&item.count>0?item:null;}
 public void removeItem(L1ItemInstance item,int n){item.count-=n;}
 public void updateItem(L1ItemInstance item,int col){}
 public void saveItem(L1ItemInstance item,int col){saves++;}
}