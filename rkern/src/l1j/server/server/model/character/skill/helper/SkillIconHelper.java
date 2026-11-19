package l1j.server.server.model.character.skill.helper;


import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;


import l1j.server.server.model.network.core.sc.packet.SC_SkillBrave;
import l1j.server.server.model.network.core.sc.packet.SC_SkillHaste;
import l1j.server.server.model.network.core.sc.packet.SC_Dexup;
import l1j.server.server.model.network.core.sc.packet.SC_Strup;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconShield;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconAura;
public final class SkillIconHelper {

	private SkillIconHelper() {}

	@FunctionalInterface
	private interface IconAction {
		void send(L1PcInstance pc, int durSec);
	}

	/** skillId 인덱스로 바로 접근하는 테이블 */
	private static final IconAction[] TABLE;

	static {
		// 등록할 엔트리들 (세분화/섞임 방지)
		final Entry[] entries = new Entry[] {
				// -------------------------
				// Shield
				// -------------------------
				shield(L1SkillId.SHIELD, 1),
				shield(L1SkillId.EARTH_GUARDIAN, 7),
				shield(L1SkillId.IRON_SKIN, 10),
				shield(L1SkillId.FIRE_SHIELD, 4),

				// -------------------------
				// Stat (Str/Dex)
				// -------------------------
				dex(L1SkillId.DRESS_DEXTERITY, 2),
				str(L1SkillId.DRESS_MIGHTY, 2),
				str(L1SkillId.PHYSICAL_ENCHANT_STR, 5),
				dex(L1SkillId.PHYSICAL_ENCHANT_DEX, 5),

				// -------------------------
				// Aura
				// -------------------------
				aura(L1SkillId.GLOWING_AURA, 113),
				aura(L1SkillId.SHINING_AURA, 114),
				aura(L1SkillId.BRAVE_AURA, 116),
				aura(L1SkillId.EARTH_WEAPON, 147),
				aura(L1SkillId.AQUA_SHOT, 148),
				aura(L1SkillId.DANCING_BLADES, 154),
				aura(L1SkillId.STORM_EYE, 155),
				aura(L1SkillId.BURNING_WEAPON, 162),
				aura(L1SkillId.STORM_SHOT, 165),

				// -------------------------
				// Haste / Slow
				// -------------------------
				haste(L1SkillId.HASTE, 1),
				haste(L1SkillId.GREATER_HASTE, 1),

				haste(L1SkillId.SLOW, 2),
				haste(L1SkillId.MOB_SLOW_1, 2),
				haste(L1SkillId.MOB_SLOW_18, 2),

				// -------------------------
				// Brave
				// -------------------------
				brave(L1SkillId.HOLY_WALK, 4),
				brave(L1SkillId.MOVING_ACCELERATION, 4),
				brave(L1SkillId.BLOOD_LUST, 6),

		};

		// 최대 skillId 계산 → 딱 그만큼만 배열 생성
		int max = 0;
		for (Entry e : entries) {
			if (e == null) continue;
			if (e.skillId < 0) continue;
			if (e.skillId > max) max = e.skillId;
		}

		final IconAction[] table = new IconAction[max + 1];

		// 중복/충돌 체크 + 등록
		for (Entry e : entries) {
			if (e == null) continue;
			final int id = e.skillId;
			if (id < 0) continue;

			if (id >= table.length) {
				// 이론상 max로 잡았으니 여기 안 와야 정상
				throw new IllegalStateException("Icon table overflow: skillId=" + id + " len=" + table.length);
			}

			if (table[id] != null) {
				// 여기 터지면: L1SkillId 상수값이 겹쳤거나, 같은 skillId를 두 번 등록한 것
				throw new IllegalStateException("Duplicate icon action for skillId=" + id);
			}
			table[id] = e.action;
		}

		TABLE = table;
	}

	// =========================
	// 외부 엔트리포인트
	// =========================
	public static void sendIcon(L1PcInstance pc, int skillId, int durSec) {
		if (pc == null) return;

		// 0초 이하: 상태 갱신만
		if (durSec <= 0) {
			SC_OwnCharStatus.send(pc);
			return;
		}

		// 범위 체크 
		if (skillId >= 0 && skillId < TABLE.length) {
			final IconAction act = TABLE[skillId];
			if (act != null) {
				try {
					act.send(pc, durSec);
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
		}

		// 최종 상태 갱신
		SC_OwnCharStatus.send(pc);
	}

	// =========================
	// Entry + factories
	// =========================
	private static final class Entry {
		final int skillId;
		final IconAction action;
		Entry(int skillId, IconAction action) {
			this.skillId = skillId;
			this.action = action;
		}
	}

	private static Entry e(int skillId, IconAction action) {
		return new Entry(skillId, action);
	}

	private static Entry shield(int skillId, int shieldType) {
		return e(skillId, (pc, d) -> SC_SkillIconShield.send(pc, shieldType, d));
	}

	private static Entry aura(int skillId, int auraId) {
		return e(skillId, (pc, d) -> SC_SkillIconAura.send(pc, auraId, d));
	}

	private static Entry str(int skillId, int amount) {
		return e(skillId, (pc, d) -> SC_Strup.send(pc, pc, amount, d));
	}

	private static Entry dex(int skillId, int amount) {
		return e(skillId, (pc, d) -> SC_Dexup.send(pc, pc, amount, d));
	}

	private static Entry haste(int skillId, int hasteType) {
		return e(skillId, (pc, d) -> {
			final int id = pc.getId();
			SC_SkillHaste.send(pc, id, hasteType, d);
			SC_SkillHaste.broadcastFast(pc, id, hasteType, 0);
		});
	}

	private static Entry brave(int skillId, int braveType) {
		return e(skillId, (pc, d) -> {
			final int id = pc.getId();
			SC_SkillBrave.send(pc, id, braveType, d);
			SC_SkillBrave.broadcastFast(pc, id, braveType, 0);
		});
	}
}
