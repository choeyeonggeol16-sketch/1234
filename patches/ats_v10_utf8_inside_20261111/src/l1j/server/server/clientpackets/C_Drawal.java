package l1j.server.server.clientpackets;

import l1j.server.WarSystem.CastleWar;
import l1j.server.WarSystem.CastleWarBusiness;
import l1j.server.server.GameClient;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.ItemId;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SystemMessage;


public class C_Drawal extends ClientBasePacket {

	private static final String C_DRAWAL = "[C] C_Drawal";

	public C_Drawal(byte abyte0[], GameClient clientthread) throws Exception {
		super(abyte0);
		try {
			@SuppressWarnings("unused")
			int i = readD();
			int j = readD();

			L1PcInstance pc = clientthread.getActiveChar();
			L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
			if (clan != null) {
				int castle_id = clan.getCastleId();
				if (castle_id != 0) {
					if (CastleWarBusiness.getInstance().isNowWar(clan.getCastleId())) {
						S_SystemMessage sm = new S_SystemMessage("공성중에는 세금을 찾으실수 없습니다.");
						pc.sendPackets(sm, true);
						return;
					}
					if (pc.getClanRank() != L1Clan.CLAN_RANK_PRINCE || !pc.isCrown() || pc.getId() != pc.getClan().getLeaderId())
						return;

					CastleWar war = CastleWarBusiness.getInstance().get(castle_id);
					int money = war.getPublicMoney();
					long _money = money;
					if (_money <= 0 || money < j) {//버그방지
						return;
					}
					money -= j;
					ItemInstance item = ItemTable.getInstance().createItem(ItemId.아데나);
					
					if (item != null) {
						war.setPublicMoney(money);
						CastleWarBusiness.getInstance().updateCastle(castle_id);
						if (pc.getInventory().checkAddItem(item, j) == L1Inventory.OK) {
							pc.getInventory().storeItem(ItemId.아데나, j);
						} else {
							L1World.getInstance().getInventory(pc.getX(), pc.getY(),pc.getMapId()).storeItem(ItemId.아데나, j);
						}
						pc.sendPackets(new S_ServerMessage(143, "$457",String.format("$4(%d)", j)));
					}
				}
			}
		} catch (Exception e) {

		} finally {
			clear();
		}
	}

	@Override
	public String getType() {
		return C_DRAWAL;
	}
}
