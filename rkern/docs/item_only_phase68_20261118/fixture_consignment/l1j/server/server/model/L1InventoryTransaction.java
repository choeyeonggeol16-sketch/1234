package l1j.server.server.model;
import l1j.server.server.model.Instance.*;
public class L1InventoryTransaction implements AutoCloseable {
 public final L1PcInstance pc;public L1InventoryTransaction(L1PcInstance pc){this.pc=pc;}
 public boolean debit(int id,int count){pc.log.add("debit-ticket:"+id+":"+count);return true;}
 public boolean debit(L1ItemInstance item,int count){pc.log.add("debit-item:"+item.getId()+":"+count);return true;}
 public void requireTables(String table){pc.log.add("table:"+table);}
 public void onFailure(Runnable cb){pc.log.add("failure-callback");}
 public void close(){pc.log.add("close");}
}