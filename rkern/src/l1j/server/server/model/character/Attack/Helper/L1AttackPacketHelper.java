package l1j.server.server.model.character.Attack.Helper;

/** Only sprite-to-projectile lookups; packet emission belongs to CAttackAction. */
public final class L1AttackPacketHelper {
    private L1AttackPacketHelper() {}

    public static int arrowGfxBySprite(int spriteId) {
        switch (spriteId) {
            case 7967: case 7968: return 7972;
            case 8900: case 11402: case 16002: return 8904;
            case 8913: case 11406: case 16074: return 8916;
            case 13631: return 13656;
            case 13635: return 13658;
            case 15814: return 12243;
            case 17535: return 17539;
            default: return 66;
        }
    }

    public static int stingGfxBySprite(int spriteId) {
        switch (spriteId) {
            case 7967: case 7968: return 7972;
            case 8900: case 11402: case 16002: return 8904;
            case 8913: case 11406: case 16074: return 8916;
            case 13631: return 13656;
            case 13635: return 13658;
            case 15814: return 12243;
            case 17535: return 17539;
            default: return 2989;
        }
    }

    public static int arrowGfxByWeaponIdWhenNoArrow(int weaponId) {
        switch (weaponId) {
            case 190: return 2349;
            case 10000: return 8771;
            case 202011: return 8916;
            default: return -1;
        }
    }
}
