package l1j.server.server.model.item.function;

import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.storage.CacheD;

/** Dedicated item action; original transaction and effect ordering preserved. */
final class ClassPackageRewardReceipt {
    private ClassPackageRewardReceipt() {}
    private static final Logger LOG = Logger.getLogger("l1j.server.server.model.item.function.EconomyRewardItems");

    public static void useClassPackage(L1PcInstance pc, L1ItemInstance packageItem) {
        if (pc == null || packageItem == null || packageItem.getItemId() != 77799
                || pc.getInventory().getItem(packageItem.getId()) != packageItem) return;
        final int type = pc.getType();
        final int classItem;
        switch (type) {
        case 0: case 1: case 4: case 5: case 7:
            classItem = 600209;
            break;
        case 2:
            classItem = pc.getAbility().getBaseDex() >= 18 ? 600210 : 600209;
            break;
        case 3: case 6:
            classItem = 600211;
            break;
        default:
            pc.sendPackets("사용이 불가능한 클래스 입니다.");
            return;
        }
        try (L1InventoryTransaction tx = User.CheckAndSetPendingUseETCItem(pc,
                CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (!tx.debit(packageItem, 1)) return;
            // Exactly the old package: class item, four materials, two 600202, one 600204.
            if (tx.grant(classItem, 1) == null
                    || tx.grant(600198, 1) == null
                    || tx.grant(600199, 1) == null
                    || tx.grant(600200, 1) == null
                    || tx.grant(600201, 1) == null
                    || tx.grant(600202, 1) == null
                    || tx.grant(600202, 1) == null
                    || tx.grant(600204, 1) == null) {
                SC_SystemMessage.send(pc, "아이템 보상을 소지할 수 없습니다.");
                return;
            }
            tx.onFailure(() -> SC_SystemMessage.send(pc, "아이템 보상 저장에 실패했습니다."));
            CDB.RequestUseItem(pc, tx, null);
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Class package exchange failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "아이템 보상을 처리하지 못했습니다.");
        }
    }
}
