package l1j.server.server.model;
import java.sql.*;
import java.util.logging.Logger;
import java.util.logging.Level;
import l1j.server.DatabaseFactory;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.*;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.templates.L1Item;
public final class BeginnerLegacy {
private static final Logger _log = Logger.getLogger("BeginnerTest");
private String getJobActivationCode(L1PcInstance pc) { return "C"; }
	public int GiveItemToActivePc(L1PcInstance pc) {
		if (pc == null) return 0;

		String sql = "SELECT * FROM beginner WHERE activate IN (?, ?)";
		String jobCode = getJobActivationCode(pc);

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {

			pstm.setString(1, "A");
			pstm.setString(2, jobCode);

			try (ResultSet rs = pstm.executeQuery()) {
				while (rs.next()) {
					int itemId = rs.getInt("item_id");
					int count = rs.getInt("count");
					int enchant = rs.getInt("enchantlvl");

					if (count <= 0) continue;

					L1Item temp = ItemTable.getInstance().getTemplate(itemId);
					if (temp == null) continue;

					if (temp.isStackable()) {
						// 겹쳐지는 아이템 (물약, 아데나 등) 처리
						L1ItemInstance item = ItemTable.getInstance().createItem(itemId);
						if (item != null) {
							item.setCount(count);
							item.setEnchantLevel(enchant);
							if (pc.getInventory().checkAddItem(item, count) == L1Inventory.OK) {
								pc.getInventory().storeItem(item);
								SC_ServerMessage.send(pc, 403, item.getLogName() + "(ID:" + itemId + ")");
							}
						}
					} else {
						// 안 겹쳐지는 장비 아이템 처리
						for (int i = 0; i < count; i++) {
							L1ItemInstance item = ItemTable.getInstance().createItem(itemId);
							if (item != null) {
								item.setEnchantLevel(enchant);
								if (pc.getInventory().checkAddItem(item, 1) == L1Inventory.OK) {
									pc.getInventory().storeItem(item);
									SC_ServerMessage.send(pc, 403, item.getLogName() + "(ID:" + itemId + ")");
								} else {
									break;
								}
							}
						}
					}
				}
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "GiveItemToActivePc error", e);
		}
		return 0;
	}
}
