package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
public class ItemReceiptOperations {
 public static Runnable receipt;
 public static L1ItemInstance pending;
 public static int submitted;
 public static void UseDurableConsume(L1PcInstance pc,L1ItemInstance item,Runnable after) {
  if (item.count<=0 || receipt!=null) return;
  pc.getInventory().item=item;receipt=after;pending=item;submitted++;
 }
 public static void finish(boolean commit) {
  Runnable r=receipt;receipt=null;
  if(r==null) return;
  if(commit){pending.count--;r.run();}
  pending=null;
 }
}
