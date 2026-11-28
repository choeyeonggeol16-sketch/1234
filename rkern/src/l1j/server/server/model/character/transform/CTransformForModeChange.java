package l1j.server.server.model.character.transform;

import l1j.server.server.ActionCodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ChangeShape;
import l1j.server.server.model.network.core.sc.packet.SC_CharVisualUpdate;

/** The existing private store body is a mode appearance over the timed transform. */
public final class CTransformForModeChange {
    private static final int[] SHOP_SPRITES = {11479, 11483, 11480, 11485, 11482, 11486, 11481, 11484};

    private CTransformForModeChange() {}

    public static int FindShopSprite(int index) {
        return index >= 1 && index <= SHOP_SPRITES.length ? SHOP_SPRITES[index - 1] : 0;
    }

    public static void SetTransformData(L1PcInstance pc, int packedSprite) {
        // Index resolution and shop registration are upstream of this owner-only callback.
        int sprite = packedSprite == 0 ? pc.getCurrentSpriteId() : packedSprite;
        pc.상점변신 = packedSprite;
        pc.setCurrentSprite(sprite);
        SC_ChangeShape.send(pc, pc.getId(), sprite, ActionCodes.ACTION_Shop);
        CTransform.broadcastShape(pc, sprite, ActionCodes.ACTION_Shop);
        long visual = SC_CharVisualUpdate.pack(pc);
        SC_CharVisualUpdate.sendPacked(pc, visual);
        if (CTransform.canBroadcastShape(pc)) SC_CharVisualUpdate.broadcastPacked(pc, visual);
    }

    public static void CancelTransform(L1PcInstance pc) {
        pc.상점변신 = 0;
        int active = pc.getTransformId();
        int sprite = active != 0 ? active : pc.getClassId();
        pc.setCurrentSprite(sprite);
        pc.getEquipment().refreshTransformWeaponAction();
        if (pc.isDead()) return;
        int action = pc.getCurrentWeapon();
        SC_ChangeShape.send(pc, pc.getId(), sprite, action);
        CTransform.broadcastShape(pc, sprite, action);
        long visual = SC_CharVisualUpdate.pack(pc, action);
        SC_CharVisualUpdate.sendPacked(pc, visual);
        if (CTransform.canBroadcastShape(pc)) SC_CharVisualUpdate.broadcastPacked(pc, visual);
    }
}
