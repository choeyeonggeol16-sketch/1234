package l1j.server.server.model.item.function;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_HOLY_MITHRIL_POWDER;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_HOLY_WATER;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_HOLY_WATER_OF_EVA;
import l1j.server.Config;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.CWorld;
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

final class RewardReceiptConsumables {
    private RewardReceiptConsumables() {}
    static void use(L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId, L1ItemInstance l1iteminstance1) {
        switch (itemId) {
		case 3000150:
			LifeEyeCraftReceipt.craftLifeEye(pc, l1iteminstance);
			break;

		case 5922794:
			ClassGiftRewardTransactions.useClassGiftBundle(pc, l1iteminstance);
			break;

		case 3000175: // 탐상자
			TamPointBoxReceipt.useTamBoxAtomic(pc, l1iteminstance);
			break;

		case 4100120:
		case 4100119:
			MoneyBoxTransactions.useBurnedCharm(pc, l1iteminstance);
			break;

		case 3000039:
		case 3000169:
		case 718:
			RandomEnchantBoxTransactions.useRandomEnchantBox(pc, l1iteminstance);
			break;

		case 698:
		case 776:
			WeaponTransformationReceipt.useWeaponTransformation(pc, l1iteminstance, l1iteminstance1);
			break;

        }
    }
}
