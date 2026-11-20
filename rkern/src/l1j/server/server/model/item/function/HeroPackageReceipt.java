package l1j.server.server.model.item.function;

import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import java.util.logging.Logger;
import l1j.server.server.model.network.core.sc.packet.SC_ItemName;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import l1j.server.server.Account;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1Quest;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CUserSession;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_NewCreateItem;
import l1j.server.server.model.network.core.sc.packet.SC_ReturnedStat;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Item;

/** Item-specific effect extracted without changing its original execution body. */
final class HeroPackageReceipt {
    private static final Logger _log = Logger.getLogger(SpecialConsumableRouter.class.getName());

    private HeroPackageReceipt() {}

    static void useHeroPackageAtomic(L1PcInstance pc, L1ItemInstance input) {
        if (pc == null || input == null || input.getItemId() != 753
                || pc.getInventory().getItem(input.getId()) != input) return;
        if (pc.getInventory().getSize() > 120) {
            SC_SystemMessage.send(pc, "소지하고 있는 아이템이 너무 많습니다.");
            return;
        }
        if (pc.getInventory().getWeight100() > 82) {
            SC_SystemMessage.send(pc, "소지품이 너무 무거워서 사용 할 수 없습니다.");
            return;
        }
        final int gift = pc.isWarrior() ? 832
                : pc.isKnight() ? 834
                : pc.isDragonknight() ? 838
                : pc.isCrown() ? 833
                : pc.isWizard() ? 836
                : pc.isBlackwizard() ? 839
                : pc.isElf() ? 835
                : pc.isDarkelf() ? 837 : 0;
        if (gift == 0) return;
        final L1Item definition = ItemTable.getInstance().getTemplate(gift);
        if (definition == null) {
            SC_SystemMessage.send(pc, "영웅 패키지 보상을 찾을 수 없습니다.");
            return;
        }
        // createItem() publishes in L1World; prepare a detached gift instead.
        final L1ItemInstance reward = new L1ItemInstance(definition, 1);
        reward.setId(l1j.server.server.IdFactory.getInstance().nextId());
        reward.setBless(1);
        reward.setIdentified(true);
        try (l1j.server.server.model.L1InventoryTransaction tx =
                User.CheckAndSetPendingUseETCItem(pc,
                        l1j.server.server.storage.CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (!tx.debit(input, 1)) return;
            final L1ItemInstance prepared = tx.grant(reward);
            if (prepared == null) {
                SC_ServerMessage.send(pc, 82);
                return;
            }
            tx.onFailure(() -> SC_SystemMessage.send(pc, "영웅 패키지 지급을 저장하지 못했습니다."));
            CDB.RequestUseItem(pc, tx, () -> {
                try {
                    try {
                        SC_ServerMessage.send(pc, 403, prepared.getLogName());
                    } catch (RuntimeException outputFailure) {
                        _log.log(Level.WARNING, "Committed hero package notice failed: " + pc.getId(),
                                outputFailure);
                    }
                } finally {
                    ItemActionCompletion.finishCommittedConsumable(pc, input);
                }
            });
        } catch (RuntimeException failure) {
            _log.log(Level.WARNING, "Hero package transaction failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "영웅 패키지 사용을 처리하지 못했습니다.");
        }
    }
}
