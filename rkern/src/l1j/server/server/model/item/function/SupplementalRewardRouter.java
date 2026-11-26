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

final class SupplementalRewardRouter {

    private SupplementalRewardRouter() {}

    static void use(L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId, L1ItemInstance l1iteminstance1) {
        switch (itemId) {
        case 3000195:
        case 474750:
        case 474751:
            TradingBoardAccessItems.use(pc, l1iteminstance, itemId, l1iteminstance1);
            break;
        case 810011:
        case 3000048:
        case 40328:
        case 45450:
        case 45451:
        case 45452:
        case 45453:
        case 12301:
        case 41354:
            EventUtilityConsumables.use(pc, l1iteminstance, itemId, l1iteminstance1);
            break;
        case 145001:
        case 145002:
        case 145003:
        case 145004:
        case 145005:
        case 145006:
        case 145007:
        case 145008:
        case 145009:
        case 145010:
            ClanRewardScrollItems.use(pc, l1iteminstance, itemId, l1iteminstance1);
            break;
        case 4100203:
        case 4100140:
        case 4100141:
        case 4100142:
        case 4100143:
        case 4100144:
            EquipmentSlotUnlockReceipt.useSlotUnlockAtomic(pc, l1iteminstance);
            break;
        case 40067:
        case 41414:
        case 40066:
        case 41413:
        case 500220:
        case 410002:
        case 40735:
        case 40042:
        case 41404:
        case 41412:
            ManaConsumables.use(pc, l1iteminstance, itemId, l1iteminstance1);
            break;
        case 111556:
        case 490028:
        case 490029:
            ExperienceAndAlignmentItems.use(pc, l1iteminstance, itemId, l1iteminstance1);
            break;
        case 1000010:
        case 1000110:
        case 1000120:
        case 40493:
        case 41019:
        case 41027:
            TemporaryEffectConsumables.use(pc, l1iteminstance, itemId, l1iteminstance1);
            break;
        case 3000150:
        case 5922794:
        case 3000175:
        case 4100120:
        case 4100119:
        case 3000039:
        case 3000169:
        case 718:
        case 698:
        case 776:
            RewardReceiptConsumables.use(pc, l1iteminstance, itemId, l1iteminstance1);
            break;
        }
    }
}
