package l1j.server.server.model.item.function;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
import l1j.server.server.model.network.core.sc.packet.SC_MpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.L1TownLocation;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.rank.business.RankBusiness;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

final class SupplementalCraftRouter {

    private SupplementalCraftRouter() {}

    static void use(CIOClient client, L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId, L1ItemInstance l1iteminstance1) {
            
        switch (itemId) {
        case 5200101:
        case 5200102:
        case 5200103:
            CardFusionChanceItems.use(client, pc, l1iteminstance, itemId, l1iteminstance1);
            break;
        case 5990:
            RankBusiness.getInstance().onExpendiantItem(pc, l1iteminstance);
            break;
        case 820018:
        case 30106:
        case 3000356:
        case 3000363:
        case 3000364:
        case 3000365:
        case 410094:
        case 210082:
            CraftingSupportAndTravelItems.use(client, pc, l1iteminstance, itemId, l1iteminstance1);
            break;
        case 753:
            HeroBundleItems.use(client, pc, l1iteminstance, itemId, l1iteminstance1);
            break;
        case 40097:
        case 40119:
        case 140119:
        case 140329:
            UncurseScrollActions.use(client, pc, l1iteminstance, itemId, l1iteminstance1);
            break;
        case 31118:
        case 31119:
        case 31120:
            DessertBuffConsumables.use(client, pc, l1iteminstance, itemId, l1iteminstance1);
            break;
        case 210083:
            CharacterSlotReceipt.useCharacterSlotExpansionAtomic(client, pc, l1iteminstance);
            break;
        case 474747:
            ConsignmentVoucherItems.use(client, pc, l1iteminstance, itemId, l1iteminstance1);
            break;
        case 60035:
        case 40925:
        case 40926:
        case 40927:
        case 40928:
        case 40929:
            TargetedCraftingAndPurificationItems.use(client, pc, l1iteminstance, itemId, l1iteminstance1);
            break;
        }
    }
}
