package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.MJActionListener.ActionListenerLinkageLoader;
import l1j.server.MJCTSystem.Loader.MJCTLoadManager;
import l1j.server.MJCTSystem.MJCTHandler;
import l1j.server.MJCompanion.Basic.Potion.MJCompanionPotionInfo;
import l1j.server.MJDTSSystem.MJDTSLoader;
import l1j.server.MJDungeonTimer.Loader.DungeonTimePotionLoader;
import l1j.server.MJPassiveSkill.MJPassiveLoader;
import l1j.server.MJRaidSystem.Loader.MJRaidCreatorLoader;
import l1j.server.PrideSystem.PrideLoadManager;
import l1j.server.server.model.Instance.L1ClanJoinInstance;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.utils.IntRange;

/** Ordered extension admission; the first matching subsystem owns the use. */
public final class ExtendedItemActions {
    private ExtendedItemActions() {}

    public static void use(CIOClient client, L1PcInstance pc, L1ItemInstance item,
            int itemObjectId, int targetId, int x, int y, String name, int skillId) {
        int itemId = item.getItem().getItemId();
        if (itemId == l1j.server.server.packets.Protocol.MainServer.PlaySupport.window.PlaySupportWindow.ITEM_ID) {
            if (item.getId() != itemObjectId) return;
            l1j.server.server.packets.Protocol.MainServer.PlaySupport.window.PlaySupportWindow.tryOpenItem(client, pc, item);
            return;
        }

        if (CIOThreadCallback.isWorldTickWand(itemId)) {
            CIOThreadCallback.getInstance().markWandActive(pc, item, targetId, x, y);
            return;
        }

        if (MJRaidCreatorLoader.getInstance().createRaid(pc, item))
            return;

        if (itemId == MJCTLoadManager.CTSYSTEM_LOAD_ID) {
            MJCTHandler.load(pc, item);
            return;
        }

        if(L1CompanionInstance.use_item(pc, item))
            return;
        MJCompanionPotionInfo pInfo = MJCompanionPotionInfo.get_potion_info(item.getItemId());
        if(pInfo != null){
            pInfo.use_item(pc, item);
            return;
        }

        if (MJPassiveLoader.getInstance().useItem(pc, item))
            return;

        if (DungeonTimePotionLoader.getInstance().use_potion(pc, item))
            return;

        if (ActionListenerLinkageLoader.getInstance().use_item(pc, item))
            return;

        if (L1ClanJoinInstance.use_item(pc, item))
            return;

        if (MJDTSLoader.getInstance().use_item(pc, item))
            return;
        if (PrideLoadManager.getInstance().use_item(pc, item))
            return;

        if(IntRange.includes(itemId, 4100255, 4100259)){
            BugRaceControlWandItems.useNumberedDownWand(pc, itemId);
            return;
        }

        CreatureItemActions.UseItem(client, pc, item, itemObjectId, targetId, x, y, name, skillId);
    }
}
