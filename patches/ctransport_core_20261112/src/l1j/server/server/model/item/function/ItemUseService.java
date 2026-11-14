package l1j.server.server.model.item.function;

import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.cs.packet.CS_ItemUse;
import l1j.server.server.utils.ItemUseUtil;

/** Coordinates the legacy use stages on the caller's existing owner thread. */
final class ItemUseService {
    private static final Logger LOG=Logger.getLogger(CS_ItemUse.class.getName());
    private ItemUseService() {}

    static void use(CIOClient client, L1PcInstance pc, L1ItemInstance item,
            int itemObjectId, int targetId, int x, int y, String name, int skillId) {
        final int itemId=item.getItem().getItemId();
        if(ItemUseUtil.Use(itemId)) {
            CItemUse.useSupplemental(client,pc,item,targetId);
            return;
        }
        if(!ItemUseTargeting.showSelection(pc,itemId)) return;
        if(!ItemUseAdmission.canUse(pc,item)) return;
        final ItemUseContext use=new ItemUseContext(client,pc,item,itemObjectId,targetId,x,y,name,skillId);
        if(LOG.isLoggable(Level.FINEST)) {
            LOG.finest("request item use (obj) = "+itemObjectId+" action = "+use.targetItemObjectId()+" value = "+use.text());
        }
        if(item.getItem().getType2()==0) {
            if(!ItemUseAdmission.canUseEtc(pc,item)) return;
            if(ItemUsePreparation.stopsDispatch(use)) return;
        }
        if(ItemUseDispatcher.use(use)==ItemUseResult.COMPLETE) ItemUseCompletion.finish(use);
    }
}
