package l1j.server.server.model.item.function;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;

import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;
import l1j.server.server.templates.L1Skills;

/** Empty level 1-5 spell scrolls: inputs and the written scroll share one receipt. */
public final class BlankMagicScrolls {
    private static final Logger LOG = Logger.getLogger(BlankMagicScrolls.class.getName());
    private static final int[] MAX_SKILL_INDEX = {7, 15, 22, 31, 39};

    private BlankMagicScrolls() {}

    public static void write(L1PcInstance pc, L1ItemInstance scroll, int skillIndex) {
        if (pc == null || scroll == null) return;
        final int scrollId = scroll.getItemId();
        if (scrollId < 40090 || scrollId > 40094) return;
        if (!pc.isWizard()) {
            SC_ServerMessage.send(pc, 264);
            return;
        }
        if (skillIndex < 0 || skillIndex > MAX_SKILL_INDEX[scrollId - 40090]) {
            SC_ServerMessage.send(pc, 591);
            return;
        }
        if (pc.getInventory().GetByServerID(scroll.getId()) != scroll || scroll.isEquipped()) return;
        final L1Skills skill = SkillsTable.getInstance().getTemplate(skillIndex + 1);
        if (skill == null) {
            SC_ServerMessage.send(pc, 79);
            return;
        }
        final int hpCost = skill.getHpConsume();
        final int mpCost = skill.getMpConsume();
        final int lawfulDelta = skill.getLawful();
        final int materialId = skill.getItemConsumeId();
        final int materialCount = skill.getItemConsumeCount();
        if (pc.getCurrentHp() < hpCost) {
            SC_ServerMessage.send(pc, 279);
            return;
        }
        if (pc.getCurrentMp() < mpCost) {
            SC_ServerMessage.send(pc, 278);
            return;
        }
        if (materialId != 0 && materialCount > 0
                && !pc.getInventory().HaveItemByClassId(materialId, materialCount)) {
            SC_ServerMessage.send(pc, 299);
            return;
        }
        try (L1InventoryTransaction tx = User.CheckAndSetPendingUseETCItem(pc,
                CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (!tx.debit(scroll, 1)) return;
            if (materialId != 0 && materialCount > 0
                    && !tx.debit(materialId, materialCount)) {
                SC_ServerMessage.send(pc, 299);
                return;
            }
            final L1ItemInstance result = tx.grant(40859 + skillIndex, 1);
            if (result == null) {
                SC_ServerMessage.send(pc, 82);
                return;
            }
            final String resultName = result.getName();
            tx.onFailure(() -> SC_SystemMessage.send(pc, "마법 기록 저장에 실패했습니다."));
            CDB.RequestUseItem(pc, tx, () -> {
                // Character resources are live game state. Deduct only after item receipt.
                pc.setCurrentHp(Math.max(0, pc.getCurrentHp() - hpCost));
                pc.setCurrentMp(Math.max(0, pc.getCurrentMp() - mpCost));
                long lawful = (long) pc.getLawful() + lawfulDelta;
                pc.setLawful((int) Math.max(-32767L, Math.min(32767L, lawful)));
                SC_SystemMessage.send(pc, resultName + "  획득");
            });
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Blank scroll transaction failed: " + pc.getId()
                    + " / " + scrollId, failure);
            SC_SystemMessage.send(pc, "마법 기록을 처리하지 못했습니다.");
        }
    }
}
