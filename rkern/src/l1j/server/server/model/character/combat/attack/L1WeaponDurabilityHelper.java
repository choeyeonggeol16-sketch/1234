package l1j.server.server.model.character.combat.attack;

import java.util.concurrent.ThreadLocalRandom;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;

public final class L1WeaponDurabilityHelper {

    private L1WeaponDurabilityHelper() {}

    private static final int NORMAL_CHANCE = 5;   // 일반/저주 무기 손상 확률(%)
    private static final int BLESSED_CHANCE = 2;  // 축복 무기 손상 확률(%)
    private static final int DAMAGE_SOUND_GFX = 10712;
    private static final int MSG_DURABILITY_DAMAGED = 268;


    public static void tryDamageWeaponDurability(
            final int calcType,
            final L1PcInstance pc,
            final L1NpcInstance targetNpc,
            final int weaponType,
            final int weaponBless,
            final L1ItemInstance weapon
    ) {
        if (!isEligible(calcType, pc, targetNpc, weaponType, weapon)) return;

        final int chance = (weaponBless == 0) ? BLESSED_CHANCE : NORMAL_CHANCE;
        if (!rollPercent(chance)) return;

        applyDamage(pc, weapon);
    }

    private static boolean isEligible(
            final int calcType,
            final L1PcInstance pc,
            final L1NpcInstance targetNpc,
            final int weaponType,
            final L1ItemInstance weapon
    ) {
        if (calcType != 2) return false;
        if (pc == null || targetNpc == null || weapon == null) return false;

        // 손상하지 않는 NPC
        if (targetNpc.getNpcTemplate() == null || !targetNpc.getNpcTemplate().is_hard()) return false;

        // 맨손/무기 없음
        if (weaponType == 0) return false;

        // 손상하지 않는 무기
        if (weapon.getItem() == null || weapon.getItem().get_canbedmg() == 0) return false;

        // 소울 오브 플레임 중 손상 없음
        if (pc.hasSkillEffect(L1SkillId.SOUL_OF_FLAME)) return false;

        return true;
    }

    private static boolean rollPercent(final int percent) {
        if (percent <= 0) return false;
        if (percent >= 100) return true;
        return ThreadLocalRandom.current().nextInt(100) < percent; // 0~99
    }

    private static void applyDamage(final L1PcInstance pc, final L1ItemInstance weapon) {
        // "당신의 %0가 손상했습니다."
        SC_ServerMessage.send(pc, MSG_DURABILITY_DAMAGED, weapon.getLogName());
        pc.getInventory().receiveDamage(weapon);
        SC_SkillSound.send(pc, pc.getId(), DAMAGE_SOUND_GFX);
    }
}