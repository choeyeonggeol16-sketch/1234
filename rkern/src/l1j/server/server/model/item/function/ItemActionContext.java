package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.templates.L1EtcItem;

/** Immutable packet and selected-item snapshot for one synchronous use.
 * Decoded fields are derived from a shared targeting mode, without a per-use array.
 * This object is never retained by a CacheD completion callback.
 */
final class ItemActionContext {
    private final CIOClient client;
    private final L1PcInstance player;
    private final L1ItemInstance item, targetItem;
    private final int itemId, itemObjectId, targetId, x, y, skillId, mode;
    private final String name;
    private final boolean delayEffect;

    ItemActionContext(CIOClient client, L1PcInstance player, L1ItemInstance item,
            int itemObjectId, int targetId, int x, int y, String name, int skillId) {
        this.client=client; this.player=player; this.item=item;
        this.itemId=item.getItem().getItemId(); this.itemObjectId=itemObjectId;
        this.targetId=targetId; this.x=x; this.y=y; this.name=name; this.skillId=skillId;
        mode=ItemTargetDecoder.modeOf(itemId,item.getItem().getUseType());
        delayEffect=item.getItem().getType2()==0 && ((L1EtcItem)item.getItem()).get_delayEffect()>0;
        // A zero target is not an item object id. Avoid the inventory's
        // linear scan for the overwhelmingly common untargeted consumable.
        final int selectedObjectId = targetItemObjectId();
        targetItem = selectedObjectId == 0 ? null : player.getInventory().getItem(selectedObjectId);
    }
    CIOClient client(){return client;}
    L1PcInstance player(){return player;}
    L1ItemInstance item(){return item;}
    L1ItemInstance targetItem(){return targetItem;}
    int itemId(){return itemId;}
    int itemObjectId(){return itemObjectId;}
    int targetId(){return targetId;}
    int x(){return x;}
    int y(){return y;}
    int skillId(){return skillId;}
    String name(){return name;}
    boolean delayEffect(){return delayEffect;}
    int targetItemObjectId(){return mode==ItemTargetDecoder.ITEM?targetId:mode==ItemTargetDecoder.DEFAULT?skillId:0;}
    String text(){return mode==ItemTargetDecoder.NAME || mode==ItemTargetDecoder.TARGET_NAME?name:"";}
    int spellTargetId(){return mode==ItemTargetDecoder.TARGET || mode==ItemTargetDecoder.POINT || mode==ItemTargetDecoder.TARGET_NAME?targetId:0;}
    int spellX(){return mode==ItemTargetDecoder.POINT?x:0;}
    int spellY(){return mode==ItemTargetDecoder.POINT?y:0;}
    int blankScrollSkillId(){return mode==ItemTargetDecoder.BLANK?skillId:0;}
    int cookingStatus(){return mode==ItemTargetDecoder.COOKING?skillId:0;}
    int cookingNumber(){return cookingStatus();}
    int fishingX(){return mode==ItemTargetDecoder.FISHING?targetId&0xffff:0;}
    int fishingY(){return mode==ItemTargetDecoder.FISHING?(targetId>>>16)&0xffff:0;}
    short bookmarkMapId(){return mode==ItemTargetDecoder.BOOKMARK?(short)(targetId&0xffff):0;}
    int bookmarkX(){return mode==ItemTargetDecoder.BOOKMARK?(targetId>>>16)&0xffff:0;}
    int bookmarkY(){return mode==ItemTargetDecoder.BOOKMARK?x:0;}
    int teleportBookSlot(){return mode==ItemTargetDecoder.BOOK?skillId:0;}
    int storedItemObjectId(){return mode==ItemTargetDecoder.STORED?targetId:0;}
}
