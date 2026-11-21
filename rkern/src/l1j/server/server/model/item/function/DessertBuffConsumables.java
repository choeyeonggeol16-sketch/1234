package l1j.server.server.model.item.function;

import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
import l1j.server.server.model.network.core.sc.packet.SC_MpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;

final class DessertBuffConsumables {
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(DessertBuffConsumables.class.getName());

    private DessertBuffConsumables() {}

    // All gameplay effects are applied before this optional presentation path.
    // Keep each packet independent; losing one packet cannot mask the others.
    private static void notifyDessert(L1PcInstance pc, int itemId) {
        if (itemId == 31120) {
            try {
                SC_SPMR.send(pc);
            } catch (RuntimeException failure) {
                LOG.log(java.util.logging.Level.WARNING, "Dessert SPMR packet failed", failure);
            }
        }
        try {
            SC_OwnCharStatus2.send(pc);
        } catch (RuntimeException failure) {
            LOG.log(java.util.logging.Level.WARNING, "Dessert status packet failed", failure);
        }
        if (itemId == 31119) {
            try {
                SC_OwnCharAttrDef.send(pc);
            } catch (RuntimeException failure) {
                LOG.log(java.util.logging.Level.WARNING, "Dessert attribute packet failed", failure);
            }
            try {
                SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
            } catch (RuntimeException failure) {
                LOG.log(java.util.logging.Level.WARNING, "Dessert ER packet failed", failure);
            }
        } else if (itemId == 31120) {
            try {
                SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
            } catch (RuntimeException failure) {
                LOG.log(java.util.logging.Level.WARNING, "Dessert MP packet failed", failure);
            }
        }
        final int sound = itemId == 31118 ? 7954 : itemId == 31119 ? 7952 : 7956;
        try {
            SC_SkillSound.send(pc, pc.getId(), sound);
        } catch (RuntimeException failure) {
            LOG.log(java.util.logging.Level.WARNING, "Dessert self sound failed", failure);
        }
        try {
            SC_SkillSound.broadcast(pc, pc.getId(), sound);
        } catch (RuntimeException failure) {
            LOG.log(java.util.logging.Level.WARNING, "Dessert sound broadcast failed", failure);
        }
    }
    static void use(CUserSocket client, L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId, L1ItemInstance l1iteminstance1) {
        switch (itemId) {
		case 31118: {
			ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
				// Remove all mutually exclusive ice effects, including stale overlaps.
				IceBuffItem.clearIceBuff(pc);
				pc.setSkillEffect(L1SkillId.완력빙수, 900000);
				pc.applySkillModifierFromOwner(L1SkillId.완력빙수, 0);
				notifyDessert(pc, 31118);
			});
			break;
		}

		case 31119: {
			ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
				// Remove all mutually exclusive ice effects, including stale overlaps.
				IceBuffItem.clearIceBuff(pc);
				pc.setSkillEffect(L1SkillId.민첩빙수, 900000);
				pc.applySkillModifierFromOwner(L1SkillId.민첩빙수, 0);
				notifyDessert(pc, 31119);

			});
			break;
		}

		case 31120: {
			ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
				// Remove all mutually exclusive ice effects, including stale overlaps.
				IceBuffItem.clearIceBuff(pc);
				pc.setSkillEffect(L1SkillId.지식빙수, 900000);
				pc.applySkillModifierFromOwner(L1SkillId.지식빙수, (3L << 32) | 1);
				notifyDessert(pc, 31120);
			});
			break;
		}

        }
    }
}
