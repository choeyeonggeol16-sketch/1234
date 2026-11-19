package l1j.server.server.model.network.core.cs.packet;
import l1j.server.server.model.*;import l1j.server.server.model.Instance.*;
public class User {public static L1InventoryTransaction CheckAndSetPendingUseETCItem(L1PcInstance pc,int key){pc.log.add("open-tx");return new L1InventoryTransaction(pc);}}
