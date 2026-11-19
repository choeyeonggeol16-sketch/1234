package l1j.server.server.model.trap;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.datatables.ItemTable;
public final class RaceCandidate {

public static void run(L1PcInstance c) {
					L1ItemInstance item = c.getInventory().storeItem(3000024, 1);
					// Preserve the one-item notification even when storeItem merged a stack.
					// Do not create a second, world-registered dummy item for the message.
					if (item != null) SC_ServerMessage.send(c, 403, item.getNumberedName(1));
				
}
}
