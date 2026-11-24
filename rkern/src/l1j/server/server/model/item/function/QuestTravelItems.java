package l1j.server.server.model.item.function;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.quest.CQuestSystem;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;
import static l1j.server.server.model.item.function.CreatureItemActions.ITEM_USE2_LOG;
import static l1j.server.server.model.item.function.CreatureItemActions._random;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsume;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsumeTwoInput;
import static l1j.server.server.model.item.function.DurabilityRepairItems.UseDurableRepair;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableTwoInput;
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.isOwnedScrollTarget;

/** Quest item domain handler extracted without changing item effects or transaction order. */
final class QuestTravelItems {
    private QuestTravelItems() {}

    static ItemActionResult handleKnightQuest(ItemActionContext use) {
        L1PcInstance pc = use.player();
        if (pc.isKnight()
                && (pc.getX() >= 32806 &&
                        pc.getX() <= 32814)
                && (pc.getY() >= 32798 && pc.getY() <= 32807) && pc.getMapId() == 13) {

            pc.start_teleport(32815, 32810, 13, 5, 169, false, false);
        } else {
            SC_ServerMessage.send(pc, 79);

        }
        return ItemActionResult.COMPLETE;
    }

    static ItemActionResult handleQuestTeleport(ItemActionContext use) {
        L1PcInstance pc = use.player();
        if (pc.getX() == 32778 && pc.getY() == 32738 && pc.getMapId() == 21) {

            pc.start_teleport(32781, 32728, 21, 5, 169, true, false);
        } else if (pc.getX() == 32781 && pc.getY() == 32728 && pc.getMapId() == 21) {

            pc.start_teleport(32778, 32738, 21, 5, 169, true, false);
        } else {
            SC_ServerMessage.send(pc, 79);
        }
        return ItemActionResult.COMPLETE;
    }

    static ItemActionResult handleLukeinRecord(ItemActionContext use) {
        L1PcInstance pc = use.player();
        if (pc.getQuest().get_step(CQuestSystem.QUEST_LUKEIN1) == 1) {
            SC_NPCTalkReturn.send(pc, pc.getId(), "firsttmap");
        } else if (pc.getQuest().get_step(CQuestSystem.QUEST_LUKEIN1) == 2) {
            SC_NPCTalkReturn.send(pc, pc.getId(), "secondtmapa");
        } else if (pc.getQuest().get_step(CQuestSystem.QUEST_LUKEIN1) == 3) {
            SC_NPCTalkReturn.send(pc, pc.getId(), "secondtmapb");
        } else if (pc.getQuest().get_step(CQuestSystem.QUEST_LUKEIN1) == 4) {
            SC_NPCTalkReturn.send(pc, pc.getId(), "secondtmapc");
        } else if (pc.getQuest().get_step(CQuestSystem.QUEST_LUKEIN1) == 5) {
            SC_NPCTalkReturn.send(pc, pc.getId(), "thirdtmapd");
        } else if (pc.getQuest().get_step(CQuestSystem.QUEST_LUKEIN1) == 6) {
            SC_NPCTalkReturn.send(pc, pc.getId(), "thirdtmape");
        } else if (pc.getQuest().get_step(CQuestSystem.QUEST_LUKEIN1) == 7) {
            SC_NPCTalkReturn.send(pc, pc.getId(), "thirdtmapf");
        } else if (pc.getQuest().get_step(CQuestSystem.QUEST_LUKEIN1) == 8) {
            SC_NPCTalkReturn.send(pc, pc.getId(), "thirdtmapg");
        } else if (pc.getQuest().get_step(CQuestSystem.QUEST_LUKEIN1) == 9) {
            SC_NPCTalkReturn.send(pc, pc.getId(), "thirdtmaph");
        } else if (pc.getQuest().get_step(CQuestSystem.QUEST_LUKEIN1) == 10) {
            SC_NPCTalkReturn.send(pc, pc.getId(), "thirdtmapi");
        }
        return ItemActionResult.COMPLETE;
    }
}
