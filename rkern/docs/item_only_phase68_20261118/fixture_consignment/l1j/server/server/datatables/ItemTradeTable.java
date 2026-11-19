package l1j.server.server.datatables;
import l1j.server.server.model.*;import l1j.server.server.model.Instance.*;
public class ItemTradeTable {private static final ItemTradeTable T=new ItemTradeTable();public static ItemTradeTable getInstance(){return T;}
 public void stageTopic(L1InventoryTransaction tx,L1PcInstance p,String date,L1ItemInstance i,int n){p.log.add("stage:"+n+":"+date);}
}