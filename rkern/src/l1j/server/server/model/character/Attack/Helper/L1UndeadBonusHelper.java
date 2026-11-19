package l1j.server.server.model.character.Attack.Helper;

import java.util.concurrent.ThreadLocalRandom;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;

/**
 * 무기 재질/축복/홀리 옵션에 의한 추가 데미지 계산 헬퍼.
 * 
 * 1) 재질(은/미스릴/오리) + 언데드(1 or 3)면
 *    - 밤 1~10, 낮 1~20 추가
 * 2) 축복(bless==0) + 언데드(1 or 2 or 3)면 1~4 추가
 * 3) weapon.getHolyDmgByMagic() != 0 + 언데드(1 or 3)면 고정 추가
 */
public final class L1UndeadBonusHelper {

    private L1UndeadBonusHelper() {}

    public static final int MATERIAL_SILVER     = 14;
    public static final int MATERIAL_MITHRIL    = 17;
    public static final int MATERIAL_ORIHARUKON = 22;

    /**
     * 가장 깔끔한 형태: 필요한 값만 받아서 계산.
     */
    public static int calcBonus(
    		L1PcInstance pc,
            final int weaponMaterial,
            final int weaponBless,
            final int undeadType,
            final boolean isNight
    ) {
        int damage = 0;

        final boolean isUndead13 = (undeadType == 1 || undeadType == 3);
        final boolean isUndead123 = (undeadType == 1 || undeadType == 2 || undeadType == 3);

        // 1) 재질 보너스 (은/미스릴/오리) AND (언데드 1/3)
        final boolean isMaterialBonusWeapon =
                (weaponMaterial == MATERIAL_SILVER
              || weaponMaterial == MATERIAL_MITHRIL
              || weaponMaterial == MATERIAL_ORIHARUKON);

        if (isMaterialBonusWeapon && isUndead13) {
            // 밤 1~10, 낮 1~20
            damage += isNight
                    ? ThreadLocalRandom.current().nextInt(1, 11)
                    : ThreadLocalRandom.current().nextInt(1, 21);
        }

        // 2) 축복 보너스 (bless==0) AND (언데드 1/2/3) -> 1~4
        if (weaponBless == 0 && isUndead123) {
            damage += ThreadLocalRandom.current().nextInt(1, 5);
        }

        // 3) 홀리 고정추가 (언데드 1/3)
        if (pc.hasSkillEffect(L1SkillId.HOLY_WEAPON) && isUndead13) {
            damage += 1;
        }
        return damage;
    }


    public static int calcBonus(L1PcInstance pc,
            final int weaponMaterial,
            final int weaponBless,
            final L1ItemInstance weapon,
            final int undeadType,
            final boolean isNight
    ) {
        return calcBonus(pc, weaponMaterial, weaponBless, undeadType, isNight);
    }
}
