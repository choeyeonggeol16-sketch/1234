package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.templates.L1EtcItem;

/** Immutable packet and selected-item snapshot for one synchronous use.
 * Decoded fields are derived from a shared targeting mode, without a per-use array.
 * This object is never retained by a CacheD completion callback.
 */
final class ItemUseContext {
    private final CIOClient client;
    private final L1PcInstance player;
    private final L1ItemInstance item, targetItem;
    private final int itemId, itemObjectId, targetId, x, y, skillId, mode;
    private final String name;
    private final boolean delayEffect;

    ItemUseContext(CIOClient client, L1PcInstance player, L1ItemInstance item,
            int itemObjectId, int targetId, int x, int y, String name, int skillId) {
        this.client=client; this.player=player; this.item=item;
        this.itemId=item.getItem().getItemId(); this.itemObjectId=itemObjectId;
        this.targetId=targetId; this.x=x; this.y=y; this.name=name; this.skillId=skillId;
        mode=ItemUseTargeting.modeOf(itemId,item.getItem().getUseType());
        delayEffect=item.getItem().getType2()==0 && ((L1EtcItem)item.getItem()).get_delayEffect()>0;
        targetItem=player.getInventory().getItem(targetItemObjectId());
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
    int targetItemObjectId(){return mode==ItemUseTargeting.ITEM?targetId:mode==ItemUseTargeting.DEFAULT?skillId:0;}
    String text(){return mode==ItemUseTargeting.NAME || mode==ItemUseTargeting.TARGET_NAME?name:"";}
    int spellTargetId(){return mode==ItemUseTargeting.TARGET || mode==ItemUseTargeting.POINT || mode==ItemUseTargeting.TARGET_NAME?targetId:0;}
    int spellX(){return mode==ItemUseTargeting.POINT?x:0;}
    int spellY(){return mode==ItemUseTargeting.POINT?y:0;}
    int blankScrollSkillId(){return mode==ItemUseTargeting.BLANK?skillId:0;}
    int cookingStatus(){return mode==ItemUseTargeting.COOKING?skillId:0;}
    int cookingNumber(){return cookingStatus();}
    int fishingX(){return mode==ItemUseTargeting.FISHING?targetId&0xffff:0;}
    int fishingY(){return mode==ItemUseTargeting.FISHING?(targetId>>>16)&0xffff:0;}
    short bookmarkMapId(){return mode==ItemUseTargeting.BOOKMARK?(short)(targetId&0xffff):0;}
    int bookmarkX(){return mode==ItemUseTargeting.BOOKMARK?(targetId>>>16)&0xffff:0;}
    int bookmarkY(){return mode==ItemUseTargeting.BOOKMARK?x:0;}
    int teleportBookSlot(){return mode==ItemUseTargeting.BOOK?skillId:0;}
    int storedItemObjectId(){return mode==ItemUseTargeting.STORED?targetId:0;}
}
