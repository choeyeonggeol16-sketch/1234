package l1j.server.server.model.Instance;
public final class L1ItemInstance {
 private final int id; private final int count;
 public L1ItemInstance(int id){this(id,1);}
 public L1ItemInstance(int id,int count){this.id=id;this.count=count;}
 public int getItemId(){return id;} public int getCount(){return count;}
 public ItemDefinition getItem(){return new ItemDefinition("i"+id);}
 public static final class ItemDefinition {final String name; ItemDefinition(String n){name=n;} public String getName(){return name;}}
}
