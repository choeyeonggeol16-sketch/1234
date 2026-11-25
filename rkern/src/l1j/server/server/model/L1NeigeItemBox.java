package l1j.server.server.model;

import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/** Class-specific reward boxes: original box debit and reward grant share a receipt. */
public final class L1NeigeItemBox {
    private static final Logger LOG = Logger.getLogger(L1NeigeItemBox.class.getName());

    private L1NeigeItemBox() {}

    public static boolean lowbox(L1PcInstance pc, L1ItemInstance box) {
        return useBox(pc, box, 875640382, false);
    }

    public static boolean herobox(L1PcInstance pc, L1ItemInstance box) {
        return useBox(pc, box, 875640407, true);
    }

    private static boolean useBox(L1PcInstance pc, L1ItemInstance box, int expectedId,
            boolean hero) {
        if (pc == null || box == null || box.getItemId() != expectedId
                || pc.getInventory().GetByServerID(box.getId()) != box) return false;
        final int reward = rewardFor(pc.getClassId(), hero);
        if (reward == 0) {
            SC_SystemMessage.send(pc, "해당 클래스는 보상 상자를 사용할 수 없습니다.");
            return false;
        }
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, box.getId())) {
            if (!tx.debit(box, 1)) return false;
            if (tx.grant(reward, 1) == null) {
                SC_SystemMessage.send(pc, "보상을 소지할 수 없습니다.");
                return false;
            }
            tx.onFailure(() -> SC_SystemMessage.send(pc, "보상 상자 저장에 실패했습니다."));
            tx.commit(() -> SC_SkillSound.send(pc, pc.getId(), 470));
            return true;
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Neige box use failed: " + pc.getId() + " / " + expectedId, failure);
            SC_SystemMessage.send(pc, "보상 상자를 처리하지 못했습니다.");
            return false;
        }
    }

    private static int rewardFor(int classId, boolean hero) {
        switch (classId) {
        case 0: case 1: return hero ? 875640408 : 875640383;
        case 61: case 48: return hero ? 875640409 : 875640384;
        case 138: case 37: return hero ? 875640410 : 875640385;
        case 734: case 1186: return hero ? 875640411 : 875640386;
        case 2786: case 2796: return hero ? 875640412 : 875640387;
        case 6658: case 6661: return hero ? 875640413 : 875640388;
        case 6671: case 6650: return hero ? 875640414 : 875640389;
        case 12490: case 12494: return hero ? 875640414 : 875640390;
        default: return 0;
        }
    }
}
