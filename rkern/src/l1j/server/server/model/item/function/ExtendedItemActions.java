package l1j.server.server.model.item.function;

import l1j.server.server.model.item.pride.PrideLoadManager;
import l1j.server.server.model.Instance.L1ClanJoinInstance;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.action.listener.ActionListenerLinkageLoader;
import l1j.server.server.model.character.companion.basic.potion.CompanionPotionInfo;
import l1j.server.server.model.character.skill.passive.PassiveLoader;
import l1j.server.server.model.item.collection.CollectionHandler;
import l1j.server.server.model.item.collection.loader.CollectionLoadManager;
import l1j.server.server.model.item.teleport.designated.DesignatedTeleportLoader;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.world.instance.dungeon.time.loader.DungeonTimePotionLoader;
import l1j.server.server.model.world.instance.raid.loader.RaidCreatorLoader;
import l1j.server.server.utils.IntRange;

/** Ordered extension admission; the first matching subsystem owns the use. */
public final class ExtendedItemActions {
    private ExtendedItemActions() {}

    public static void use(CUserSocket client, L1PcInstance pc, L1ItemInstance item,
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

        if (RaidCreatorLoader.getInstance().createRaid(pc, item))
            return;

        if (itemId == CollectionLoadManager.CTSYSTEM_LOAD_ID) {
            CollectionHandler.load(pc, item);
            return;
        }

        if(L1CompanionInstance.use_item(pc, item))
            return;
        CompanionPotionInfo pInfo = CompanionPotionInfo.get_potion_info(item.getItemId());
        if(pInfo != null){
            pInfo.use_item(pc, item);
            return;
        }

        if (PassiveLoader.getInstance().useItem(pc, item))
            return;

        if (DungeonTimePotionLoader.getInstance().use_potion(pc, item))
            return;

        if (ActionListenerLinkageLoader.getInstance().use_item(pc, item))
            return;

        if (L1ClanJoinInstance.use_item(pc, item))
            return;

        if (DesignatedTeleportLoader.getInstance().use_item(pc, item))
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
