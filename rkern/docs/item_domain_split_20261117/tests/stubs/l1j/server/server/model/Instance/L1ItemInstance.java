package l1j.server.server.model.Instance;
import l1j.server.server.templates.L1Item;
public class L1ItemInstance {
 public int id, itemId,count,enchant,bless,attr; public boolean identified, expired;
 public L1ItemInstance(L1Item item,int n){itemId=item.getItemId();count=n;}
 public int getItemId(){return itemId;} public int getId(){return id;} public void setId(int i){id=i;}
 public void setEnchantLevel(int n){enchant=n;} public void setBless(int n){bless=n;}
 public void setAttrEnchantLevel(int n){attr=n;} public void setIdentified(boolean v){identified=v;}
 public boolean isStackable(){return false;}
 public String getLogName(){return "Item"+itemId;}
}
