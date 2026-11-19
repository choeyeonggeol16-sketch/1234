package l1j.server.server.model.Instance;
import l1j.server.server.templates.L1Item;
public class L1ItemInstance {
 private final int id; private final L1Item item; private int count=1,enchant; private boolean identified=true;
 public L1ItemInstance(int id,L1Item item){this.id=id;this.item=item;}
 public int getId(){return id;}public L1Item getItem(){return item;}
 public int getCount(){return count;}public void setCount(int v){count=v;}
 public void setEnchantLevel(int v){enchant=v;}public int getEnchantLevel(){return enchant;}
 public String getLogName(){return getNumberedName(count);}
 public String getNumberedName(int n){return "Item:"+item.itemId+" *"+n+" e"+enchant;}
 public void setIdentified(boolean b){identified=b;}public boolean isIdentified(){return identified;}
}