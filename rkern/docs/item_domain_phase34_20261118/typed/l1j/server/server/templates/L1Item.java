package l1j.server.server.templates;
public class L1Item {
 public final int itemId; public final boolean stack;
 public L1Item(int id,boolean st){itemId=id;stack=st;}
 public boolean isStackable(){return stack;}
}