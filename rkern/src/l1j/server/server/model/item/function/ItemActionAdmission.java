package l1j.server.server.model.item.function;

import java.sql.Timestamp;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1EtcItem;

/** Ordered delay, ticket, level and class admission. No effects or new pending gate. */
final class ItemActionAdmission {
    private ItemActionAdmission() {}

    static boolean canUse(L1PcInstance pc, L1ItemInstance item) {
        if(pc.getCurrentHp()<=0) return false;
        final int itemId=item.getItem().getItemId();
        int delay_id = 0;
        if (item.getItem().getType2() == 0) {
            if (item.getItem() instanceof L1EtcItem) {
                delay_id = ((L1EtcItem) item.getItem()).get_delayid();
            }
        }
        if (delay_id != 0) {
            if (pc.hasItemDelay(delay_id)) {
                return false;
            }
        }



        if (itemId >= 8000000 && itemId <= 9000000) {
            return false;
        }

        if (item.getItem().getType2() == 0) {
            int delayEffect = ((L1EtcItem) item.getItem()).get_delayEffect();
            if (delayEffect > 0) {

                Timestamp lastUsed = item.getLastUsed();
                if (lastUsed != null) {
                    final long now = System.currentTimeMillis();
                    final long remainingSeconds = delayEffect - (now - lastUsed.getTime()) / 1000;
                    if (remainingSeconds >= 0) {
                        SC_SystemMessage.send(pc, (remainingSeconds / 60) + "분 "
                                + (remainingSeconds % 60) + "초 후에 사용할 수 있습니다. ");
                        return false;
                    }
                }
            }
        }
        return true;
    }

    static boolean canUseEtc(L1PcInstance pc, L1ItemInstance item) {
        final int itemId=item.getItem().getItemId();
        int item_minlvl = ((L1EtcItem) item.getItem()).getMinLevel();
        int item_maxlvl = ((L1EtcItem) item.getItem()).getMaxLevel();
        if (item_minlvl != 0 && item_minlvl > pc.getLevel() && !pc.isGm()) {
            SC_SystemMessage.send(pc, item_minlvl + "레벨 이상이 되면 사용할 수 있는 아이템입니다.");
            SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
            return false;
        } else if (item_maxlvl != 0 && item_maxlvl < pc.getLevel() && !pc.isGm()) {
            SC_SystemMessage.send(pc, item_maxlvl + "레벨 이하일 때만 사용할 수 있는 아이템입니다.");
            SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
            return false;
        }
        if ((itemId == 40576 && !pc.isElf()) || (itemId == 40577 && !pc.isWizard())
                || (itemId == 40578 && !pc.isKnight())) {
            SC_ServerMessage.send(pc, 264);
            return false;
        }
        return true;
    }
}
