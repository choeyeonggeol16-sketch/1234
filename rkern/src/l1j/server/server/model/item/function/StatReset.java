package l1j.server.server.model.item.function;

import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.network.core.sc.packet.SC_CharVisualUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_ReturnedStat;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.CommonUtil;

public final class StatReset {
    private static final Logger LOG = Logger.getLogger(StatReset.class.getName());
    private static final int CANDLE_ITEM_ID = 200000;
    private static final int RESET_MAP_ID = 5166;

    private StatReset() {}

    public static void useCandle(L1PcInstance pc, L1ItemInstance item) {
        if (pc == null || item == null || item.getItemId() != CANDLE_ITEM_ID
                || pc.getInventory().GetByServerID(item.getId()) != item) return;
        if (pc.getLevel() < 51) {
            SC_SystemMessage.send(pc, "스텟초기화는 51레벨 이상만 가능합니다.");
            return;
        }
        if (!pc.getMap().isSafetyZone(pc.getLocation())) {
            SC_SystemMessage.send(pc, "안전한 지역에서만 사용할 수 있습니다.");
            return;
        }
        beginReset(pc, item, false);
    }

    public static void useNpcCandle(L1PcInstance pc) {
        if (pc.getLevel() < 55) {
            SC_SystemMessage.send(pc, "스텟초기화는 55레벨 이상만 가능합니다.");
            return;
        }
        L1ItemInstance candle = pc.getInventory().GetFirstUnequippedItemByClassID(CANDLE_ITEM_ID);
        if (candle == null) {
            SC_ServerMessage.send(pc, 1290);
            return;
        }
        beginReset(pc, candle, true);
    }

    private static void beginReset(L1PcInstance pc, L1ItemInstance candle, boolean viaNpc) {
        final int level = pc.getLevel();
        if (level != pc.getHighLevel()) {
            SC_SystemMessage.send(pc, "레벨이 다운된 캐릭입니다. 레벨업 후 이용하세요.");
            return;
        }
        if (pc.getReturnStat() != 0) return;
        try {
            // The receipt's character lane drains this progression image first.
            // A restart after the candle debit must be able to reopen the reset.
            if (!pc.saveIfReady()) {
                SC_SystemMessage.send(pc, "다른 저장 처리가 끝난 뒤 다시 시도해 주세요.");
                return;
            }
            try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, candle.getId())) {
                final int exp = pc.getExp();
                final int ownerId = pc.getId();
                final String account = pc.getAccountName(), name = pc.getName();
                if (exp == 0 || pc.getReturnStat() != 0 || pc.getLevel() != level
                        || pc.getHighLevel() != level || !tx.debit(candle, 1)) return;
                tx.requireTables("characters");
                tx.sql(con -> {
                    try (var ps = con.prepareStatement("UPDATE characters SET ReturnStat=? "
                            + "WHERE objid=? AND COALESCE(ReturnStat,0)=0 AND Exp=? AND level=? AND HighLevel=? "
                            + "AND account_name=? AND char_name=?")) {
                        ps.setInt(1, exp);
                        ps.setInt(2, ownerId);
                        ps.setInt(3, exp);
                        ps.setInt(4, level);
                        ps.setInt(5, level);
                        ps.setString(6, account);
                        ps.setString(7, name);
                        if (ps.executeUpdate() != 1) throw new SQLException("Reset candle character state changed");
                    }
                });
                tx.onFailure(() -> SC_SystemMessage.send(pc, "촛불 사용을 저장하지 못했습니다. 아이템을 확인해 주세요."));
                tx.commitAfterInventoryRelease(() -> {
                    pc.lockUserState();
                    try {
                        pc.setReturnStat(exp);
                        pc.publishPermanentStatRevision();
                    } finally {
                        pc.unlockUserState();
                    }
                }, () -> {
                    try {
                        if (viaNpc) {
                            pc.start_teleport(32723 + CommonUtil.random(-10, 10), 32815 + CommonUtil.random(-10, 10),
                                    RESET_MAP_ID, 5, 169, true, false);
                        } else {
                            pc.start_teleport(32723 + CommonUtil.random(10), 32851 + CommonUtil.random(10),
                                    RESET_MAP_ID, pc.getHeading(), 169, false, false);
                        }
                    } finally {
                        try {
                            prepareResetScreen(pc);
                        } finally {
                            if (!viaNpc) ItemActionCompletion.finishCommittedConsumable(pc, candle);
                        }
                    }
                });
            }
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Stat reset candle admission failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "촛불 사용을 처리하지 못했습니다.");
        }
    }

    private static void prepareResetScreen(L1PcInstance pc) {
        L1SkillUse.cast(pc, L1SkillId.CANCELLATION, pc.getId(), pc.getX(), pc.getY(),
                null, 0, L1SkillUse.TYPE_LOGIN);
        if (pc.getWeapon() != null) pc.UnequipItem(pc.getWeapon());
        for (L1ItemInstance item : pc.getInventory().GetEquippedItems()) {
            if (item.isEquipped()) pc.UnequipItem(item);
        }
        SC_CharVisualUpdate.sendPacked(pc, SC_CharVisualUpdate.pack(pc));
        SC_SPMR.send(pc);
        SC_OwnCharAttrDef.send(pc);
        SC_OwnCharStatus2.send(pc);
        SC_ReturnedStat.sendPc(pc, SC_ReturnedStat.START);
    }
}
