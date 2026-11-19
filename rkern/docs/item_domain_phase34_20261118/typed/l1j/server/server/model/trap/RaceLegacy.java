package l1j.server.server.model.trap;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.datatables.ItemTable;
public final class RaceLegacy {

public static void run(L1PcInstance c) {
					c.getInventory().storeItem(3000024, 1);

					L1ItemInstance item = ItemTable.getInstance().createItem(3000024); 
					item.setCount(1);
					SC_ServerMessage.send(c, 403, item.getLogName()); 
				
}
}
