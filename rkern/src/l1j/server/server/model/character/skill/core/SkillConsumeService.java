package l1j.server.server.model.character.skill.core;

import l1j.server.MJPassiveSkill.MJPassiveID;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.templates.L1Skills;
import l1j.server.server.utils.CalcStat;

public enum SkillConsumeService {
	INSTANCE;

	/** HP/MP 소비 가능 체크 + su 내부 _mpConsume/_hpConsume 세팅 */
	public boolean checkHpMpConsume(L1SkillUse su) {
		if (su == null) return false;

		final L1Skills skill = su.exSkill();
		if (skill == null) return false;

		int mpConsume = skill.getMpConsume();
		int hpConsume = skill.getHpConsume();

		int currentMp;
		int currentHp;

		// ===== NPC =====
		if (su.hdUser() instanceof L1NpcInstance) {
			final L1NpcInstance npc = su.tfNpc();
			if (npc == null) return false;

			currentMp = npc.getCurrentMp();
			currentHp = npc.getCurrentHp();

			su.exSetMpConsume(Math.max(0, mpConsume));
			su.exSetHpConsume(Math.max(0, hpConsume));

		} else {
			// ===== PC =====
			final L1PcInstance pc = su.hdPlayer();
			if (pc == null) return false;

			currentMp = pc.getCurrentMp();
			currentHp = pc.getCurrentHp();

			// INT에 따른 MP 절감
			try {
				double int_pre = CalcStat.엠소모감소(pc.getAbility().getInt()) * 0.01;
				int min_mp = (int) (mpConsume * int_pre);
				if (min_mp > 0) mpConsume -= min_mp;
			} catch (Throwable ignore) {}

			// 패시브 예외
			if (su.hdSkillId() == L1SkillId.ARMOR_BRAKE && pc.isPassive(MJPassiveID.ARMOR_BREAK_DESTINY.toInt())) {
				mpConsume = 20;
				hpConsume = 35;
			}

			// 장비 반감
			final L1PcInventory inv = pc.getInventory();
			if (inv != null) {
				if ((su.hdSkillId() == L1SkillId.PHYSICAL_ENCHANT_DEX || su.hdSkillId() == L1SkillId.HASTE) && inv.isEquippedDirect(20013)) {
					mpConsume >>= 1;
				}
				if ((su.hdSkillId() == L1SkillId.HEAL || su.hdSkillId() == L1SkillId.EXTRA_HEAL) && inv.isEquippedDirect(20014)) {
					mpConsume >>= 1;
				}
				if ((su.hdSkillId() == L1SkillId.ENCHANT_WEAPON || su.hdSkillId() == L1SkillId.DETECTION
						|| su.hdSkillId() == L1SkillId.PHYSICAL_ENCHANT_STR) && inv.isEquippedDirect(20015)) {
					mpConsume >>= 1;
				}
				if (su.hdSkillId() == L1SkillId.HASTE && inv.isEquippedDirect(20008)) {
					mpConsume >>= 1;
				}
				if (su.hdSkillId() == L1SkillId.GREATER_HASTE && inv.isEquippedDirect(20023)) {
					mpConsume >>= 1;
				}
			}

			if (skill.getMpConsume() > 0) {
				mpConsume = Math.max(mpConsume, 1);
			}

			su.exSetMpConsume(Math.max(0, mpConsume));
			su.exSetHpConsume(Math.max(0, hpConsume));
		}

		if (currentHp < su.exGetHpConsume() + 1) {
			if (su.hdUser() instanceof L1PcInstance && su.hdPlayer() != null) {
				SC_ServerMessage.send(su.hdPlayer(), 279);
			}
			return false;
		}

		if (currentMp < su.exGetMpConsume()) {
			if (su.hdUser() instanceof L1PcInstance && su.hdPlayer() != null) {
				SC_ServerMessage.send(su.hdPlayer(), 278);
			}
			return false;
		}

		return true;
	}

	/** 아이템 소비 가능 체크 */
	public boolean checkItemConsume(L1SkillUse su) {
		if (su == null) return false;

		final L1PcInstance pc = su.hdPlayer();
		if (pc == null) return false;

		final L1Skills skill = su.exSkill();
		if (skill == null) return false;

		final int itemConsume = skill.getItemConsumeId();
		final int itemConsumeCount = skill.getItemConsumeCount();

		if (itemConsume == 0) return true;

		final L1PcInventory inv = pc.getInventory();
		if (inv == null) return false;

		// 82레벨 미만 대체(초보자) 아이템 로직 적용
		final int actualItemId = getActualConsumeItemId(pc, inv, itemConsume, itemConsumeCount);

		return inv.checkItem(actualItemId, itemConsumeCount);
	}

	/** 실제 차감(재검사 X) */
	public void commitConsume(L1SkillUse su) {
		if (su == null) return;

		final L1Skills skill = su.exSkill();
		if (skill == null) return;

		// ===== NPC =====
		if (su.hdUser() instanceof L1NpcInstance) {
			final L1NpcInstance npc = su.tfNpc();
			if (npc == null) return;

			npc.applyNpcHpDelta(-su.exGetHpConsume());
			npc.setCurrentMp(npc.getCurrentMp() - su.exGetMpConsume());
			return;
		}

		// ===== PC =====
		final L1PcInstance pc = su.hdPlayer();
		if (pc == null) return;

		pc.setCurrentHp(pc.getCurrentHp() - su.exGetHpConsume());
		pc.setCurrentMp(pc.getCurrentMp() - su.exGetMpConsume());

		int lawful = pc.getLawful() + skill.getLawful();
		if (lawful > 32767) lawful = 32767;
		else if (lawful < -32767) lawful = -32767;
		pc.setLawful(lawful);

		final int itemConsume = skill.getItemConsumeId();
		final int itemConsumeCount = skill.getItemConsumeCount();

		// 요구 아이템이 없거나, AI(자동사냥) 상태면 아이템 차감을 하지 않음
		if (itemConsume == 0) return;

		final L1PcInventory inv = pc.getInventory();
		if (inv == null) return;

		// 82레벨 미만 대체(초보자) 아이템 로직 적용
		final int actualItemId = getActualConsumeItemId(pc, inv, itemConsume, itemConsumeCount);

		inv.consumeItem(actualItemId, itemConsumeCount);
	}

	private int getActualConsumeItemId(L1PcInstance pc, L1PcInventory inv, int originalItemId, int count) {
		// 82레벨 이상은 대체 아이템 혜택을 받지 못함
		if (pc.getLevel() >= 82) {
			return originalItemId;
		}

		int substituteId = 0;
		switch (originalItemId) {
		case 40318:  substituteId = 30079; break; // 마력의 돌 -> 초보자 마력의 돌
		case 40321:  substituteId = 30080; break; // 흑요석 -> 초보자 흑요석
		case 210035: substituteId = 30081; break; // 각인의 뼈조각 -> 초보자 각인의 뼈조각
		case 210038: substituteId = 30082; break; // 속성석 -> 초보자 속성석
		case 40319:  substituteId = 30078; break; // 정령옥 -> 초보자 정령옥
		default: return originalItemId;
		}

		// 인벤토리에 대체 아이템이 요구 수량만큼 있는지 확인
		if (inv.checkItem(substituteId, count)) {
			return substituteId;
		}

		return originalItemId;
	}
}