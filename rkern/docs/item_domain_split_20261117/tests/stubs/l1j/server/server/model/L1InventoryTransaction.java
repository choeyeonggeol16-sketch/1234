package l1j.server.server.model;
import l1j.server.server.model.Instance.*;
public class L1InventoryTransaction implements AutoCloseable {
 public static int failAtGrant=-1; public static boolean failDebit=false;
 private int grants=0; private boolean committed=false; public static int commits=0;
 public static L1InventoryTransaction begin(L1PcInstance pc,Object receipt){System.out.print("BEGIN ");return new L1InventoryTransaction();}
 public boolean debit(L1ItemInstance item,int count){System.out.print("DEBIT:"+item.getItemId()+":"+count+" ");return !failDebit;}
 public L1ItemInstance grant(L1ItemInstance item){grants++;System.out.print("GRANT:"+item.itemId+":"+item.count+":"+item.enchant+":"+item.bless+":"+item.attr+":"+item.identified+":"+item.expired+" ");return grants==failAtGrant?null:item;}
 public void onFailure(Runnable r){} public void commit(Runnable after){committed=true;commits++;System.out.print("COMMIT ");after.run();}
 public void close(){System.out.print(committed?"CLOSE ":"ROLLBACK ");}
}
