package l1j.server.server.model.item.function;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.Level;
import l1j.server.server.datatables.ItemTradeTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import java.util.logging.Logger;

final class ConsignmentItemActions {
    private static final Logger _log = Logger.getLogger(SpecialConsumableRouter.class.getName());
    private ConsignmentItemActions() {}

    private static final DateTimeFormatter CONSIGNMENT_DAY =
            DateTimeFormatter.ofPattern("yy/MM/dd", Locale.KOREA);

	static void register(L1PcInstance pc, L1ItemInstance l1iteminstance, L1ItemInstance l1iteminstance1) {
		if (l1iteminstance == null || l1iteminstance.getItemId() != 474747
				|| l1iteminstance1 == null || l1iteminstance1.getItem() == null
				|| l1iteminstance == l1iteminstance1) {
			SC_SystemMessage.send(pc, "위탁 판매할 아이템을 다시 확인해 주세요.");
			return;
		}
        String dTime = LocalDate.now().format(CONSIGNMENT_DAY);
		if (!l1iteminstance1.getItem().isTradable()) {
			SC_SystemMessage.send(pc, "교환불가능 아이템은 판매 할수 없습니다.");
			return;
		}
		if (l1iteminstance1.get_durability() > 0) {
			SC_SystemMessage.send(pc, "손상된 아이템은 판매가 불가합니다.");
			return;
		}

		if (l1iteminstance1.isEquipped()) {
			SC_SystemMessage.send(pc, "착용중인 아이템은 판매 할수 없습니다.");
			return;
		}
		if (l1iteminstance1.getBless() > 3) {
			SC_SystemMessage.send(pc, "봉인된 아이템은 판매 할 수 없습니다.");
			return;
		}
		if (l1iteminstance1.getEndTime() != null) {
			SC_SystemMessage.send(pc, "시간제 아이템은 판매 할 수 없습니다.");
			return;
		}
		/*
		 * if (l1iteminstance1.getLastUsed() != null) { pc.sendPackets(new
		 * SC_SystemMessage( "시간제 아이템은 판매 할 수 없습니다.")); return; }
		 */
		if (l1iteminstance1.getItem().getMaxUseTime() > 0) {
			SC_SystemMessage.send(pc, "시간제 아이템은 판매 할 수 없습니다.");
			return;
		}
		Object[] dollList1 = pc.getDollList().values().toArray();
		for (Object dollObject : dollList1) {
			if (dollObject instanceof L1DollInstance) {
				L1DollInstance doll = (L1DollInstance) dollObject;
				if (l1iteminstance1.getId() == doll.getItemObjId()) {
					SC_SystemMessage.send(pc, "소환중인 인형은 판매할 수 없습니다.");
					return;
				}
			}
		}

		if (l1iteminstance1.getItem().getType2() == 1 || l1iteminstance1.getItem().getType2() == 2
				|| l1iteminstance1.getItem().getType2() == 0 && (l1iteminstance1.getItem().getType() == 10)
				|| (l1iteminstance1.getItem().getItemId() >= 950000
				&& l1iteminstance1.getItem().getItemId() <= 950019)
				|| (l1iteminstance1.getItem().getItemId() >= 4100007
				&& l1iteminstance1.getItem().getItemId() <= 4100010)
				|| (l1iteminstance1.getItem().getItemId() == 900075)
				|| (l1iteminstance1.getItem().getItemId() >= 510219
				&& l1iteminstance1.getItem().getItemId() <= 510222)
				|| (l1iteminstance1.getItem().getItemId() >= 742 && l1iteminstance1.getItem().getItemId() <= 746)
				|| (l1iteminstance1.getItem().getItemId() >= 755 && l1iteminstance1.getItem().getItemId() <= 757)
				|| (l1iteminstance1.getItem().getItemId() >= 3000086
				&& l1iteminstance1.getItem().getItemId() <= 3000088)
				|| (l1iteminstance1.getItem().getItemId() >= 3000351
				&& l1iteminstance1.getItem().getItemId() <= 3000352)
				|| (l1iteminstance1.getItem().getItemId() == 447016)
				|| (l1iteminstance1.getItem().getItemId() == 447017)
				|| (l1iteminstance1.getItem().getItemId() == 410173)
				|| (l1iteminstance1.getItem().getItemId() == 4200050)
				|| (l1iteminstance1.getItem().getItemId() == 4200040)
				|| (l1iteminstance1.getItem().getItemId() == 4200060)
				|| (l1iteminstance1.getItem().getItemId() == 4200030)
				|| (l1iteminstance1.getItem().getItemId() >= 875640295
                && l1iteminstance1.getItem().getItemId() <= 875640330)
                || (l1iteminstance1.getItem().getItemId() == 4200051)
				|| (l1iteminstance1.getItem().getItemId() == 875640380)
				|| (l1iteminstance1.getItem().getItemId() >= 875640331
				&& l1iteminstance1.getItem().getItemId() <= 875640341)
				|| (l1iteminstance1.getItem().getItemId() >= 875640446
				&& l1iteminstance1.getItem().getItemId() <= 875640448)
				) {
			final int count = pc.getInventory().countItems(474747);
			if (count <= 0) {
				SC_SystemMessage.send(pc, "위탁판매 증서가 부족합니다.");
				return;
			}
			try (l1j.server.server.model.L1InventoryTransaction tx =
					User.CheckAndSetPendingUseETCItem(pc,
							l1j.server.server.storage.CacheD.accountOrderKey("board-item:", pc.getId()))) {
				if (!tx.debit(474747, count) || !tx.debit(l1iteminstance1, 1)) {
					SC_SystemMessage.send(pc, "판매 아이템이나 위탁판매 증서를 확인해 주세요.");
					return;
				}
				// board_item and character_items must commit or roll back together.
				tx.requireTables("board_item");
				ItemTradeTable.getInstance().stageTopic(tx, pc, dTime, l1iteminstance1, count);
				tx.onFailure(() -> SC_SystemMessage.send(pc,
						"위탁판매 등록에 실패했습니다. 아이템은 소모되지 않았습니다."));
				CDB.RequestUseItem(pc, tx, () -> {
			if (l1iteminstance1.getItem().getType2() == 0 && l1iteminstance1.getItem().getUseType() == 73) {
				SC_SystemMessage.send(pc, "[" + l1iteminstance1.getName() + "\\fH] 물품이 게시판에 등록되었습니다.");
			} else if (l1iteminstance1.getItem().getType2() == 1) {
				SC_SystemMessage.send(pc, "[" + l1iteminstance1.getLogName() + "\\fH] 물품이 게시판에 등록되었습니다.");
			} else {
				SC_SystemMessage.send(pc, "[" + l1iteminstance1.getName() + "\\fH] 물품이 게시판에 등록되었습니다.");
			}
			String ggg = null;
			if(count >= 10){
				int sa = (int)count/10;
				int sb = sa*10;
				int sc = (count-sb)*1000;
				if(sc == 0){
					ggg = sa+"억";
				}else{
					ggg =sa+"억"+sc+"만 아데나";
				}
			}else{
				int sd = count*1000;
				ggg = sd+"만 아데나";
			}

			try(var headerShared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
SC_SystemMessage.nativeWriter("\\f:========== 해당물품이 등록되었습니다 ========== "),Integer.MIN_VALUE,Integer.MAX_VALUE);
var priceShared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
SC_SystemMessage.nativeWriter("판매금액: \\aA"+ggg),Integer.MIN_VALUE,Integer.MAX_VALUE);
var nameShared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
SC_SystemMessage.nativeWriter("\\f:판매물품: [" + l1iteminstance1.getName() + "\\f:] "),Integer.MIN_VALUE,Integer.MAX_VALUE);
var logShared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
SC_SystemMessage.nativeWriter("\\f:판매물품: [" + l1iteminstance1.getLogName() + "\\f:] "),Integer.MIN_VALUE,Integer.MAX_VALUE)) {
for (L1PcInstance pc1 : L1World.getInstance().getAllPlayers()) {
				if (pc1.물품멘트) {
					if (l1iteminstance1.getItem().getType2() == 0 && l1iteminstance1.getItem().getUseType() == 73) {

						l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(headerShared,pc1);
						l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(nameShared,pc1);
						l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(priceShared,pc1);

					} else if (l1iteminstance1.getItem().getType2() == 1) {

						l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(headerShared,pc1);
						l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(logShared,pc1);
						l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(priceShared,pc1);

					} else {

						if (l1iteminstance1.getItem().getType2() == 0
								&& l1iteminstance1.getItem().getType() == 10) {
							l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(headerShared,pc1);
							l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(nameShared,pc1);
							l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(priceShared,pc1);

						} else {
							l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(headerShared,pc1);
							l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(logShared,pc1);
							l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(priceShared,pc1);

						}
					}
				}
			}
}
				});
			} catch (RuntimeException failure) {
				_log.log(Level.WARNING, "Consignment listing prepare failed: " + pc.getId(), failure);
				SC_SystemMessage.send(pc, "위탁판매 등록을 처리하지 못했습니다. 아이템을 확인해 주세요.");
			}
		} else {

			SC_SystemMessage.send(pc, "----------------[판매가능품목]----------------");
			SC_SystemMessage.send(pc, "무기/방어구/3단인형~5단(용포함)/마법서/카드/수호성");
			SC_SystemMessage.send(pc, "-------------------------------------------");
			SC_SystemMessage.send(pc, "판매할 수 없는 물품입니다.");
		}
	}

}
