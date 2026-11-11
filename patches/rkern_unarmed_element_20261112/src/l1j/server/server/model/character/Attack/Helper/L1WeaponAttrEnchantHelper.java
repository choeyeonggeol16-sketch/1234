package l1j.server.server.model.character.Attack.Helper;

import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.templates.L1Skills;

/**
 * 무기 속성 인챈트 추가 대미지 계산 (PC-PC / PC-NPC 공용)
 */
public final class L1WeaponAttrEnchantHelper {

	private L1WeaponAttrEnchantHelper() {}

	public static double calcAttrEnchantDamage(
			final int attrLevel,
			final int arrowItemId,           // 없으면 0
			final L1PcInstance targetPc,   
			final L1NpcInstance targetNpc  
			) {
		if (attrLevel <= 0) return 0;

		// 1) 내 속성 구간 판정 
		final AttrType type = AttrType.fromAttrLevel(attrLevel);
		if (type == AttrType.NONE) return 0;

		int dmg = 1 + (type.offsetInBand(attrLevel) << 1); // 1, 3, 5, 7, 9

		// 2) 특수 화살 3000516 보너스 +3 (PC/NPC 공통)
		if (arrowItemId == 3000516) {
			dmg += 3;
		}

		// 3) 타겟이 PC인 경우: 해당 속성 저항 %로 감산 
		if (targetPc != null) {
			final int resist = type.getPcResist(targetPc);
			return resistedBonus(dmg, resist);
		}

		// 4) NPC: 약점이 아니어도 속성 추타는 적용. 약점 비트는 해당 속성 저항 -50으로 처리.
		if (targetNpc != null) {
			final int weak = targetNpc.getNpcTemplate().get_weakAttr();
			final int npcResist = type.getNpcResist(targetNpc)
					- (type.isEffectiveToNpcWeak(weak) ? 50 : 0);
			return resistedBonus(dmg, npcResist);
		}

		return 0;
	}

    private static int resistedBonus(int bonus, int resistance) {
        // The only owner of physical enchant resistance and its FLOOR.
        // Signed weakness is preserved; only the positive side is capped.
        final long scale = 100L - Math.min(100, resistance);
        return (int) (((long) bonus * scale) / 100L);
    }

	// =========================
	// internal
	// =========================

	private enum AttrType {
		FIRE(1, 5),
		WATER(6, 10),
		WIND(11, 15),
		EARTH(16, 20),
		NONE(0, 0);

		final int min;
		final int max;

		AttrType(int min, int max) {
			this.min = min;
			this.max = max;
		}

		static AttrType fromAttrLevel(int attrLevel) {
			if (attrLevel >= 1 && attrLevel <= 5) return FIRE;
			if (attrLevel >= 6 && attrLevel <= 10) return WATER;
			if (attrLevel >= 11 && attrLevel <= 15) return WIND;
			if (attrLevel >= 16 && attrLevel <= 20) return EARTH;
			return NONE;
		}

		int offsetInBand(int attrLevel) {
			// 1~5 => 0..4, 6~10 => 0..4 ...
			return attrLevel - min;
		}

		int getPcResist(L1PcInstance pc) {
			switch (this) {
			case FIRE:  return pc.getResistance().getFire();
			case WATER: return pc.getResistance().getWater();
			case WIND:  return pc.getResistance().getWind();
			case EARTH: return pc.getResistance().getEarth();
			default:    return 0;
			}
		}

		boolean isEffectiveToNpcWeak(int npcWeakAttr) {
			switch (this) {
			case FIRE:  return (npcWeakAttr & L1Skills.ATTR_FIRE) != 0;   
			case WATER: return (npcWeakAttr & L1Skills.ATTR_WATER) != 0; 
			case WIND:  return (npcWeakAttr & L1Skills.ATTR_WIND) != 0;  
			case EARTH: return (npcWeakAttr & L1Skills.ATTR_EARTH) != 0; 
			default:    return false;
			}
		}

		int getNpcResist(L1NpcInstance npc) {
			switch (this) {
			case FIRE:  return npc.getResistance().getFire();
			case WATER: return npc.getResistance().getWater();
			case WIND:  return npc.getResistance().getWind();
			case EARTH: return npc.getResistance().getEarth();
			default:    return 0;
			}
		}
	}
}
