package l1j.server.server.model.Instance;
public class L1ItemInstance {
 public final L1Item item = new L1Item();public int id=1,bless=1,durability=0;public boolean equipped=false;public Object endTime;
 public int getItemId(){return item.itemId;}public int getId(){return id;}public L1Item getItem(){return item;}
 public int get_durability(){return durability;}public boolean isEquipped(){return equipped;}
 public int getBless(){return bless;} public Object getEndTime(){return endTime;}
 public String getName(){return "Thing";} public String getLogName(){return "+9 Thing";}
 public static final class L1Item {public boolean tradable=true;public int type2=1,type=0,useType=0,maxUseTime=0,itemId=345;
  public boolean isTradable(){return tradable;}public int getType2(){return type2;}public int getType(){return type;}
  public int getItemId(){return itemId;}public int getUseType(){return useType;}public int getMaxUseTime(){return maxUseTime;}
 }
}