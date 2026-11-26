package l1j.server.server.model.item.function;

import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.storage.CacheD;

/** Dedicated item action; original transaction and effect ordering preserved. */
final class AdenaCurrencyConversionReceipt {
    private AdenaCurrencyConversionReceipt() {}
    private static final Logger LOG = Logger.getLogger("l1j.server.server.model.item.function.EconomyRewardItems");

    public static void convertAdena(L1PcInstance pc, L1ItemInstance bank) {
        if (pc == null || bank == null
                || pc.getInventory().GetByServerID(bank.getId()) != bank) return;
        final int id = bank.getItemId();
        if (id != 400253 && id != 875640445) return;
        final int cost = id == 400253 ? 100_000_000 : 10_000_000;
        final int reward = id == 400253 ? 400254 : 600254;
        final L1ItemInstance adena = pc.getInventory().GetFirstItemByClassID(L1ItemId.ADENA);
        if (adena == null || adena.getCount() < cost
                || pc.getInventory().GetByServerID(adena.getId()) != adena) {
            SC_SystemMessage.send(pc, id == 400253
                    ? "100,000,000원 아데나가 부족합니다."
                    : "10,000,000원 아데나가 부족합니다.");
            return;
        }
        if (adena.getBless() >= 128) {
            SC_SystemMessage.send(pc, "봉인된 아데나로는 수표전환이 불가합니다.");
            SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "봉인된 아데나로는 수표전환이 불가합니다.");
            return;
        }
        try (L1InventoryTransaction tx = User.CheckAndSetPendingUseETCItem(pc,
                CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (!tx.debit(adena, cost)) return;
            if (tx.grant(reward, 1) == null) {
                SC_SystemMessage.send(pc, "수표를 소지할 수 없습니다.");
                return;
            }
            tx.onFailure(() -> SC_SystemMessage.send(pc, "아데나 수표 전환 저장에 실패했습니다."));
            CDB.RequestUseItem(pc, tx, () -> {
                SC_SkillSound.send(pc, pc.getId(), 470);
                SC_SystemMessage.send(pc, id == 400253
                        ? "1억 아데나가 수표로 전환 되었습니다."
                        : "1천만 아데나가 수표로 전환 되었습니다.");
            });
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Adena conversion failed: " + pc.getId() + " / " + id, failure);
            SC_SystemMessage.send(pc, "아데나 수표 전환을 처리하지 못했습니다.");
        }
    }
}
