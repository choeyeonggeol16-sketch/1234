package l1j.server.server.model.character.transform;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;

/** Existing L1 equipment masks and combat restrictions for a form. */
public record CTransformCombatData(int weaponMask, int armorMask, boolean canUseSkill) {
    private static final int BOW = 256;
    private static final int[] WEAPONS = {0, 2, 1, 4, BOW, 16, 8, 32, BOW, BOW, BOW, 128, 64,
            BOW, 16, 8, 32, 512, 1024};
    private static final int[] ARMOR = {0, 1, 16, 8, 32, 256, 1024, 128, 2, 512, 64, 0, 4};

    public boolean canUseWeapon(int type) {
        return canUse(WEAPONS, weaponMask, type);
    }

    public boolean canUseArmor(int type) {
        return canUse(ARMOR, armorMask, type);
    }

    private static boolean canUse(int[] types, int mask, int type) {
        return type < 0 || type >= types.length || types[type] == 0 || (mask & types[type]) != 0;
    }

    public boolean canAttackWithoutWeapon() {
        return weaponMask != BOW;
    }

    /** A dragon knight's native chainsword action is actor-specific. */
    public static int weaponAction(L1Character actor, L1ItemInstance weapon) {
        var template = weapon.getItem();
        int action = template.getType1();
        return action == 50 && template.getType() == 18 && actor instanceof L1PcInstance pc && pc.isDragonknight()
                && pc.getTransformId() == 0 ? 83 : action;
    }
}
