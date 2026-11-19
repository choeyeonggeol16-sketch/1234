package l1j.server.server.model.item.function;

import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.cs.packet.CS_ItemUse;
import l1j.server.server.utils.SupplementalItemIds;

/** Coordinates the legacy use stages on the caller's existing owner thread. */
final class ItemActionService {
    private static final Logger LOG=Logger.getLogger(CS_ItemUse.class.getName());
    private ItemActionService() {}

    static void use(CIOClient client, L1PcInstance pc, L1ItemInstance item,
            int itemObjectId, int targetId, int x, int y, String name, int skillId) {
        final int itemId=item.getItem().getItemId();
        if(SupplementalItemIds.contains(itemId)) {
            SupplementalItemActions.use(client,pc,item,targetId);
            return;
        }
        if(!ItemTargetDecoder.showSelection(pc,itemId)) return;
        if(!ItemActionAdmission.canUse(pc,item)) return;
        // Keep the diagnostic value/order without allocating a context (or scanning
        // the target inventory) for an item rejected by admission or handled by
        // the starter-support or treasure-box fast path.
        if (LOG.isLoggable(Level.FINEST)) {
            final int mode = ItemTargetDecoder.modeOf(itemId, item.getItem().getUseType());
            final int targetObjectId = mode == ItemTargetDecoder.ITEM ? targetId
                    : mode == ItemTargetDecoder.DEFAULT ? skillId : 0;
            final String text = mode == ItemTargetDecoder.NAME
                    || mode == ItemTargetDecoder.TARGET_NAME ? name : "";
            LOG.finest("request item use (obj) = " + itemObjectId
                    + " action = " + targetObjectId + " value = " + text);
        }
        if (item.getItem().getType2() == 0) {
            if (!ItemActionAdmission.canUseEtc(pc, item)) return;
            if (ItemActionPreparation.stopsDispatch(pc, item, itemId)) return;
        }
        final ItemActionContext use = new ItemActionContext(
                client, pc, item, itemObjectId, targetId, x, y, name, skillId);
        if (ItemActionDispatcher.use(use) == ItemActionResult.COMPLETE)
            ItemActionCompletion.finish(use);
    }
}
