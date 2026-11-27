package l1j.server.server.model.item.function;

import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.inventory.CUserInventory;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.logd.CLogD;

final class EnchantmentOutcomeEffects {
    private static final Logger LOG = Logger.getLogger(EnchantmentOutcomeEffects.class.getName());

    private EnchantmentOutcomeEffects() {}

    static void SuccessEnchant(L1PcInstance pc, L1ItemInstance item, CUserSocket client, int delta) {
        final int previous = item.getEnchantLevel();
        item.setEnchantLevel(Math.addExact(previous, delta));
        try {
            pc.getInventory().saveItem(item, CUserInventory.COL_ENCHANTLVL);
        } catch (RuntimeException failure) {
            item.setEnchantLevel(previous);
            throw failure;
        }
        if (pc.getEquipment().applyEquippedEnchantChange(item, previous)) {
            try { SC_SPMR.send(pc); }
            catch (RuntimeException failure) { noticeFailed("SP/MR", failure); }
        }
        pc.getInventory().updateItem(item, CUserInventory.COL_ENCHANTLVL);
        showSuccess(pc, item, previous, delta);
    }

    /** The committed receipt already applied the level, equipment delta and item packet. */
    static void SuccessEnchantCommitted(L1PcInstance pc, L1ItemInstance item, CUserSocket client, int delta) {
        showSuccess(pc, item, item.getEnchantLevel() - delta, delta);
    }

    private static void showSuccess(L1PcInstance pc, L1ItemInstance item, int previous, int delta) {
        final int current = item.getEnchantLevel();
        final int type = item.getItem().getType2();
        if (current > item.getItem().get_safeenchant()) audit(pc, item, true);
        if (type == 2) sendStatus(pc);
        if (Config.MASTERENCHANTMESS && current >= 10) {
            final String text = "어느 아덴 용사 님께서 +" + previous + " " + item.getName() + " ";
            try { SC_SystemMessage.broadcast(text + "\\fH인챈트에 성공하였습니다."); }
            catch (RuntimeException failure) { noticeFailed("broadcast", failure); }
            try { SC_PacketBox.broadcastGreen(text + "\\f2인챈트에 성공하였습니다."); }
            catch (RuntimeException failure) { noticeFailed("green broadcast", failure); }
        }
        if (delta > 0 && previous != 0) {
            final String color = type == 2 ? "$252" : "$245";
            final String intensity = (delta > 1 ? "$248" : "$247") + (type == 2 ? " " : "");
            try { SC_ServerMessage.send(pc, 161, resultName(item, previous), color, intensity); }
            catch (RuntimeException failure) { noticeFailed("success", failure); }
        }
    }

    static boolean resetsOnFailure(L1ItemInstance item) {
        final int id = item.getItemId();
        return id >= 1115 && id <= 1118;
    }

    static void FailureEnchant(L1PcInstance pc, L1ItemInstance item, CUserSocket client) {
        final int previous = item.getEnchantLevel();
        final boolean reset = resetsOnFailure(item);
        if (reset) {
            pc.UnequipItem(item);
            item.setEnchantLevel(0);
            try {
                pc.getInventory().saveItem(item, CUserInventory.COL_ENCHANTLVL);
            } catch (RuntimeException failure) {
                item.setEnchantLevel(previous);
                throw failure;
            }
            pc.getInventory().updateItem(item, CUserInventory.COL_ENCHANTLVL);
        } else if (pc.getInventory().DeleteItemAmount(item, item.getCount()) == 0) {
            return;
        }
        failureCommitted(pc, item, previous, reset);
    }

    static void failureCommitted(L1PcInstance pc, L1ItemInstance item, int previous, boolean reset) {
        if (reset) {
            try { SC_ServerMessage.send(pc, 1310); }
            catch (RuntimeException failure) { noticeFailed("reset", failure); }
        } else {
            audit(pc, item, false);
            final String color = item.getItem().getType2() == 2 ? " $252" : "$245";
            try { SC_ServerMessage.send(pc, 164, resultName(item, previous), color); }
            catch (RuntimeException failure) { noticeFailed("destruction", failure); }
        }
        sendStatus(pc);
    }

    private static String resultName(L1ItemInstance item, int enchant) {
        final String name = item.getName();
        if (!item.isIdentified() || enchant == 0) return name;
        return (enchant > 0 ? "+" : "") + enchant + " " + name;
    }

    private static void sendStatus(L1PcInstance pc) {
        try { SC_OwnCharStatus.send(pc); }
        catch (RuntimeException failure) { noticeFailed("character status", failure); }
    }

    private static void audit(L1PcInstance pc, L1ItemInstance item, boolean success) {
        try { CLogD.getInstance().addEnchant(pc, item, success); }
        catch (RuntimeException failure) { LOG.log(Level.WARNING, "Enchant audit log failed", failure); }
    }

    private static void noticeFailed(String operation, RuntimeException failure) {
        LOG.log(Level.WARNING, "Enchant " + operation + " notification failed", failure);
    }
}
