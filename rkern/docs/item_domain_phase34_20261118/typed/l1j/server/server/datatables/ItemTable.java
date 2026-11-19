package l1j.server.server.datatables;
import java.util.*;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.L1World;
import l1j.server.server.templates.L1Item;
public class ItemTable {
 static final ItemTable instance=new ItemTable();public static ItemTable getInstance(){return instance;}
 public static int creations;private static int next=1000;
 public static void reset(){creations=0;next=1000;}
 public L1Item getTemplate(int id){return id<=0?null:new L1Item(id,id==101 || id==3000024);}
 public L1ItemInstance createItem(int id){var template=getTemplate(id);if(template==null)return null;
   var v=new L1ItemInstance(next++,template);creations++;L1World.getInstance().storeObject(v);return v;}
}