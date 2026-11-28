package l1j.server.server.model.character.skill.acquire;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.CSkillDB;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.sc.packet.SC_AddSkill;
import l1j.server.server.model.network.core.sc.packet.SC_HorunOK;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.mysql.CSkillAcquireStorage;
import l1j.server.server.templates.L1Skills;

/** Player-side permanent acquisition. The existing inventory receipt owns cost and publication. */
public final class CSkillAcquisition {
    private static final Logger LOG = Logger.getLogger(CSkillAcquisition.class.getName());

    private CSkillAcquisition() {}

    public static int[] skillListPacket(int[] acquiredIds) {
        CSkillDB definitions = CSkillDB.getInstance();
        int[] levels = new int[30];
        for (int skillId : acquiredIds) {
            L1Skills skill = definitions.getTemplate(skillId);
            if (skill == null || skill.getSkillLevel() < 1 || skill.getSkillLevel() > 29) continue;
            int slot = skillId == 234 ? skill.getSkillLevel() : skill.getSkillLevel() - 1;
            levels[slot] |= skill.getId();
        }
        return levels;
    }

    public static void publish(L1PcInstance pc, List<L1Skills> skills) {
        if (skills.size() == 1) {
            pc.AcquireSkill(skills.getFirst().getSkillId());
        } else if (!skills.isEmpty()) {
            pc.AcquireSkills(skills.stream().mapToInt(L1Skills::getSkillId).toArray());
        }
    }

    public static void learnFromNpc(L1PcInstance pc, int selection, boolean exchange) {
        if (selection == 0 || pc.isItemTransferBusy()) return;
        CSkillDB definitions = CSkillDB.getInstance();
        int eligible = selection & definitions.acquireDB().npcLearnMask(pc.getType(), pc.getLevel(), exchange);
        int pending = eligible;
        for (int remaining = eligible; remaining != 0; remaining &= remaining - 1) {
            int skillId = Integer.numberOfTrailingZeros(remaining) + 1;
            if (pc.HaveSkill(skillId)) pending &= ~(1 << (skillId - 1));
        }
        if (pending == 0) return;

        try (L1InventoryTransaction tx = L1InventoryTransaction.beginCharacterStat(pc)) {
            int price = CSkillAcquireDB.npcPrice(pending, exchange);
            int learnedMask = pending;
            int notifications = 0;
            int previous = 0;
            if (exchange) {
                // Horun charges the full selection price for each first-tier spell.
                // Higher tiers keep their existing free grant and there is no spell 24.
                for (int remaining = pending & 255; remaining != 0; remaining &= remaining - 1) {
                    int bit = Integer.lowestOneBit(remaining);
                    if (tx.debit(L1ItemId.PANS_LEATHER, price)) previous = Integer.numberOfTrailingZeros(bit) + 1;
                    else {
                        learnedMask &= ~bit;
                        notifications++;
                    }
                }
            } else if (!tx.debit(L1ItemId.ADENA, price)) {
                SC_ServerMessage.send(pc, 189);
                return;
            }
            final int flags = learnedMask;
            final int rejected = notifications;
            final int lastTierOne = previous;
            if (flags == 0) {
                for (int i = 0; i < rejected; i++) SC_HorunOK.send(pc, lastTierOne);
                return;
            }
            List<L1Skills> skills = new ArrayList<>(Integer.bitCount(flags));
            for (int remaining = flags; remaining != 0; remaining &= remaining - 1)
                skills.add(definitions.getTemplate(Integer.numberOfTrailingZeros(remaining) + 1));
            tx.requireTables("character_skills");
            tx.sql(con -> CSkillAcquireStorage.insertBatch(con, pc.getId(), skills, false));
            tx.onFailure(() -> SC_SystemMessage.send(pc, "스킬 습득을 저장하지 못했습니다."));
            tx.commit(() -> {
                publish(pc, skills);
                if (exchange) {
                    for (int i = 0; i < rejected; i++) SC_HorunOK.send(pc, lastTierOne);
                } else {
                    SC_SkillSound.send(pc, pc.getId(), 224);
                    int[] levels = new int[30];
                    levels[0] = flags & 255;
                    levels[1] = (flags >>> 8) & 255;
                    levels[2] = (flags >>> 16) & 255;
                    SC_AddSkill.send(pc, levels, pc.isElf() ? pc.getElfAttr() : 0);
                }
            });
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Could not prepare NPC skill learning for " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "스킬 습득을 처리하지 못했습니다.");
        }
    }
}
