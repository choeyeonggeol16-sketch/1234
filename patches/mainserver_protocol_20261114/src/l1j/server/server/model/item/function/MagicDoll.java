package l1j.server.server.model.item.function;

import l1j.server.Config;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.doll.DollRegistry;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.packets.Protocol.Packet.SC_DOLL_ON_OFF;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Npc;

public final class MagicDoll {

    private static final Logger LOG = Logger.getLogger(MagicDoll.class.getName());
    private static final int MAGIC_CRYSTAL = 41246;
    private static final int DOLL_TIME_SECONDS = 1800;

    private MagicDoll() {}

    public static void useMagicDoll(L1PcInstance pc, L1ItemInstance item) {
        if (pc == null || item == null || pc.isInvisble()
                || pc.getInventory().getItem(item.getId()) != item) return;

        final long now = System.currentTimeMillis();
        if (pc.getQuizTime3() + 1000 > now) return;
        pc.setQuizTime3(now);

        final DollRegistry.Def def = DollRegistry.byItemId(item.getItemId());
        if (def == null) return;

        final int itemObjectId = item.getId();
        L1DollInstance called = null;
        for (L1DollInstance doll : pc.getDollList().values()) {
            if (doll != null && doll.getItemObjId() == itemObjectId) {
                called = doll;
                break;
            }
        }
        if (called != null) {
            dismissDoll(pc, called);
            return;
        }
        summonDoll(pc, item, def);
    }

    private static void summonDoll(L1PcInstance pc, L1ItemInstance item, DollRegistry.Def def) {
        final int mapId = pc.getMapId();
        if (mapId == 30000 || mapId == 30001) {
            SC_SystemMessage.send(pc, "현재 인형을 소환할 수 없는 장소입니다.");
            return;
        }
        if (mapId == 5490) {
            SC_SystemMessage.send(pc, "낚시터에서는 인형 소환이 불가 합니다.");
            return;
        }

        final int maxLevelExclusive = def.getMaxLevelExclusive();
        if (maxLevelExclusive > 0 && pc.getLevel() >= maxLevelExclusive) {
            SC_SystemMessage.send(pc, 
                    "수련자 마법인형은 " + maxLevelExclusive + "레벨 이상 사용할 수 없습니다.");
            return;
        }

        final int maxCount = Config.MAX_DOLL_COUNT <= 0 ? 1 : Config.MAX_DOLL_COUNT;
        if (pc.getDollList().size() >= maxCount && !Config.인형소환유무) {
            SC_SystemMessage.send(pc, "먼저 소환 된 인형을 해제하시길 바랍니다.");
            return;
        }

        final int consumeCount = def.getCrystalCost();
        if (consumeCount < 0) return;
        if (consumeCount > 0 && !pc.getInventory().checkItem(MAGIC_CRYSTAL, consumeCount)) {
            SC_ServerMessage.send(pc, 337, "$5240");
            return;
        }

        final L1Npc template = NpcTable.getInstance().getTemplate(def.getNpcId());
        if (template == null) return;

        if (consumeCount == 0) {
            summonAfterDebit(pc, item, def, template, maxCount);
            return;
        }
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, item.getId())) {
            if (!tx.debit(MAGIC_CRYSTAL, consumeCount)) {
                SC_ServerMessage.send(pc, 337, "$5240");
                return;
            }
            tx.onFailure(() -> SC_SystemMessage.send(pc,
                    "마법인형 소환 재료를 저장하지 못했습니다."));
            // Constructing a doll changes live world state. It must not happen
            // until the crystal debit has committed and released its owner gate.
            tx.commitAfterInventoryRelease(() ->
                    summonAfterDebit(pc, item, def, template, maxCount));
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Magic doll material debit failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "마법인형 소환을 처리하지 못했습니다.");
        }
    }

    private static void summonAfterDebit(L1PcInstance pc, L1ItemInstance item,
            DollRegistry.Def def, L1Npc template, int maxCount) {
        if (pc.getDollList().size() >= maxCount) {
            if (!Config.인형소환유무) return;
            final Object[] snapshot = pc.getDollList().values().toArray();
            for (Object o : snapshot) {
                if (o instanceof L1DollInstance) {
                    ((L1DollInstance) o).deleteDoll(false);
                }
            }
        }
        final L1DollInstance doll = new L1DollInstance(
                template, pc, item.get_item_level(), def.getDollType(),
                item.getId(), DOLL_TIME_SECONDS);
        final long ss = SC_SkillSound.pack(doll.getId(), 5935);
        SC_SkillSound.sendPacked(pc, ss);
        SC_SkillSound.broadcastFastPacked(pc, ss);
        SC_DOLL_ON_OFF.sendCreate(pc, doll, item, DOLL_TIME_SECONDS, true);
    }

    private static void dismissDoll(L1PcInstance pc, L1DollInstance doll) {
        if (pc == null || doll == null) return;
        doll.deleteDoll(false);
        SC_DOLL_ON_OFF.sendCreate(pc, null, null, 0, false);
        SC_OwnCharStatus.send(pc);
    }
}
