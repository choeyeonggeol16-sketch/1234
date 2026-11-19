package l1j.server.server.model.network.core.cs.packet;
import l1j.server.server.model.*;import l1j.server.server.model.Instance.*;
public class CDB{public static void RequestUseItem(L1PcInstance pc,L1InventoryTransaction tx,Runnable cb){pc.log.add("commit");cb.run();}}
