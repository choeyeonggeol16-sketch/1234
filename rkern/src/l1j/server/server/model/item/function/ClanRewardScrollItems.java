package l1j.server.server.model.item.function;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_HOLY_MITHRIL_POWDER;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_HOLY_WATER;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_HOLY_WATER_OF_EVA;
import l1j.server.Config;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_Board;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

final class ClanRewardScrollItems {
    private ClanRewardScrollItems() {}
    static void use(L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId, L1ItemInstance l1iteminstance1) {
        switch (itemId) {
		case 145001:

			ClanBuffScrollTransactions.useClanBuff(pc, l1iteminstance);

			break;

		case 145002:
			ClanBuffScrollTransactions.useClanBuff(pc, l1iteminstance);
			break;

		case 145003:
			ClanBuffScrollTransactions.useClanBuff(pc, l1iteminstance);
			break;

		case 145004:
			ClanBuffScrollTransactions.useClanBuff(pc, l1iteminstance);
			break;

		case 145005:
			ClanBuffScrollTransactions.useClanBuff(pc, l1iteminstance);
			break;

		case 145006:
			ClanBuffScrollTransactions.useClanBuff(pc, l1iteminstance);
			break;

		case 145007:
			ClanBuffScrollTransactions.useClanBuff(pc, l1iteminstance);
			break;

		case 145008:
			ClanBuffScrollTransactions.useClanBuff(pc, l1iteminstance);
			break;

		case 145009:
			ClanBuffScrollTransactions.useClanBuff(pc, l1iteminstance);
			break;

		case 145010: {
			final L1Clan pointsClan = pc.getClan();
			if (pointsClan == null || pc.getClanid() == 0) {
				pc.sendPackets("혈맹에 가입되어 있지않아 사용할 수 없습니다.");
				return;
			}
			final int pointRange = Config.혈맹포인트최대 - Config.혈맹포인트최소;
			if (pointRange <= 0) {
				pc.sendPackets("혈맹포인트 상자 설정 오류입니다.");
				return;
			}
			final int points = java.util.concurrent.ThreadLocalRandom.current().nextInt(pointRange) + Config.혈맹포인트최소;
			if (points <= 0) return;
			try (l1j.server.server.model.inventory.L1InventoryTransaction tx =
					l1j.server.server.model.inventory.L1InventoryTransaction.begin(pc,
						l1j.server.server.storage.CacheD.accountOrderKey("item-use:", pc.getId()))) {
				if (!tx.debit(l1iteminstance, 1)) return;
				ClanTable.stageWarPointDelta(tx, pointsClan, points);
				tx.onFailure(() -> pc.sendPackets("혈맹포인트 상자 사용을 저장하지 못했습니다. 아이템을 확인해 주세요."));
                tx.commit(() -> {
                    try {
                        // Economic state may fail: preserve the existing exception behavior.
                        pointsClan.addWarPointCommitted(points);
                        // Notifications are presentation only and must not invalidate the receipt.
                        try (var shared = new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                                SC_PacketBox.nativeGreenWriter("\\fH" + pc.getName()
                                        + "\\f2님이 혈맹포인트상자에서 \\fH[" + points + "]\\f2 포인트를 획득하였습니다."),
                                Integer.MIN_VALUE, Integer.MAX_VALUE)) {
                            try (var systemShared = new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                                    SC_SystemMessage.nativeWriter("\\fH" + pc.getName()
                                            + "\\f2님이 혈맹포인트상자에서 \\fH[" + points + "]\\f2 포인트를 획득하였습니다."),
                                    Integer.MIN_VALUE, Integer.MAX_VALUE)) {
                                for (L1PcInstance member : pointsClan.getOnlineClanMember()) {
                                    try {
                                        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared, member);
                                        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(systemShared, member);
                                    } catch (RuntimeException outputFailure) {
                                        java.util.logging.Logger.getLogger(ClanRewardScrollItems.class.getName())
                                                .log(java.util.logging.Level.WARNING,
                                                        "Committed clan point notice failed: " + pc.getId(), outputFailure);
                                    }
                                }
                            }
                        } catch (RuntimeException outputFailure) {
                            java.util.logging.Logger.getLogger(ClanRewardScrollItems.class.getName())
                                    .log(java.util.logging.Level.WARNING,
                                            "Committed clan point broadcast failed: " + pc.getId(), outputFailure);
                        }
                    } finally {
                        ItemActionCompletion.finishCommittedConsumable(pc, l1iteminstance);
                    }
                });
			} catch (RuntimeException failure) {
				pc.sendPackets("혈맹포인트 상자 사용에 실패했습니다. 아이템을 확인해 주세요.");
			}
			break;
		}

        }
    }
}
