package l1j.server.server.model.character.skill.core;

import l1j.server.server.datatables.PolyTable;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.ControlSkillEffects;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.character.skill.helper.SkillUseHelper;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public enum SkillUsabilityPolicy {

	INSTANCE;

	public boolean checkUsabilityByType(L1SkillUse su, int type) {
		if (type == L1SkillUse.TYPE_NORMAL) {
			return canCastNormal(su);
		}
		if (type == L1SkillUse.TYPE_SPELLSC) {
			return canCastSpellScroll(su);
		}
		// TYPE_NPCBUFF, LOGIN, GMBUFF 등
		return true; 
	}

	// =========================================================
	// 주문서
	// =========================================================

	public boolean canCastSpellScroll(L1SkillUse su) {
		if (su == null || su.hdPlayer() == null) return false;

		final L1PcInstance pc = su.hdPlayer();

		if (ControlSkillEffects.blocksCast(pc)) return false;
		if ((pc.isInvisble() || pc.isInvisDelay() && !SkillUseHelper.canCastWithInvis(su.hdSkillId()))) {
			return false;
		}

		return true;
	}

	// =========================================================
	// 일반 스킬
	// =========================================================

	public boolean canCastNormal(L1SkillUse su) {
		if (su == null || su.hdUser() == null) return false;

		// =====================================================
		// PC
		// =====================================================
		if (su.hdUser() instanceof L1PcInstance) {
			final L1PcInstance pc = su.hdPlayer();
			final int skillId = su.hdSkillId();

			// 행동 불가
			if (ControlSkillEffects.blocksCast(pc)) return false;

			// 변신 상태 스킬 사용 가능 여부
			final L1PolyMorph poly = PolyTable.getInstance().getTemplate(pc.getCurrentSpriteId());
			if (poly != null && !poly.canUseSkill()) {
				SC_ServerMessage.send(pc, 285);
				return false;
			}

			// 무게 제한
			if (!pc.is_top_ranker() && pc.getInventory().getWeight100() > 82) {
				SC_ServerMessage.send(pc, 316);
				return false;
			}

			// 공성장 제한
			if (L1CastleLocation.getCastleIdByArea(pc) != 0 && SkillUseHelper.isBlockedInCastleWar(skillId)) {
				SC_SystemMessage.send(pc, "공성장에서 사용 할 수 없습니다.");
				return false;
			}

			// 세이프존 제한
			final L1Map map = pc.getMap();
			if (map != null && map.isSafetyZone(pc.getLocation()) && SkillUseHelper.isBlockedInSafetyZone(skillId)) {
				SC_SystemMessage.send(pc, "이 마법은 마을에서는 사용할 수 없습니다.");
				return false;
			}

			if (!checkServerSpecificRestrictions(su, pc)) {
				return false;
			}

			// 속성 마법
			if (!checkAttrAgrees(su)) {
				return false;
			}

			if (skillId == L1SkillId.ELEMENTAL_PROTECTION && pc.getElfAttr() == 0) {
				SC_ServerMessage.send(pc, 280);
				return false;
			}

			// 침묵
			if (skillId != L1SkillId.TRUE_TARGET) {
				final boolean silenced = pc.hasSkillEffect(L1SkillId.SILENCE)
						|| pc.hasSkillEffect(L1SkillId.AREA_OF_SILENCE)
						|| pc.hasSkillEffect(L1SkillId.STATUS_POISON_SILENCE);

				if (silenced && !SkillUseHelper.canUseWhileSilenced(skillId)) {
					SC_ServerMessage.send(pc, 285);
					return false;
				}
			}

			// 무기 제한
			final L1ItemInstance weapon = pc.getWeapon();
			if (skillId == L1SkillId.INFERNO && !SkillUseHelper.weaponTypeIs(weapon, 1)) {
				SC_ServerMessage.send(pc, 1413);
				return false;
			}

			if ((skillId == L1SkillId.COUNTER_BARRIER || skillId == L1SkillId.SHOCK_STUN) 
					&& !SkillUseHelper.weaponTypeIs(weapon, 3)) {
				SC_ServerMessage.send(pc, 1008);
				return false;
			}

			if (skillId == L1SkillId.DANCING_BLADES || skillId == L1SkillId.SAND_STORM) {
				if (weapon == null || (!SkillUseHelper.weaponTypeIs(weapon, 1) && !SkillUseHelper.weaponTypeIs(weapon, 2))) {
					SC_SystemMessage.send(pc, "무기(검)을 착용시 사용가능합니다.");
					return false;
				}
			}

			// 컨퓨전
			if (pc.hasSkillEffect(L1SkillId.CONFUSION)) {
				SC_ServerMessage.send(pc, 285);
				return false;
			}

			// 기본 스킬 재료
			if (!pc.isGm() && !SkillConsumeService.INSTANCE.checkItemConsume(su)) {
				SC_ServerMessage.send(pc, 299);
				return false;
			}

			// =====================================================
			// NPC
			// =====================================================
		} else if (su.hdUser() instanceof L1NpcInstance) {
			if (su.hdSkillId() != L1SkillId.TRUE_TARGET) {
				final L1Character u = su.hdUser();

				if (u.hasSkillEffect(L1SkillId.CONFUSION)) {
					u.removeSkillEffect(L1SkillId.CONFUSION);
					return false;
				}
				if (u.hasSkillEffect(L1SkillId.SILENCE)) {
					u.removeSkillEffect(L1SkillId.SILENCE);
					return false;
				}
			}
		}

		// -----------------------------------------------------
		// 최종 HP / MP 체크
		// -----------------------------------------------------
		return SkillConsumeService.INSTANCE.checkHpMpConsume(su);
	}

	// =========================================================
	// 서버 전용 사용 제한
	// =========================================================

	private boolean checkServerSpecificRestrictions(L1SkillUse su, L1PcInstance pc) {
		final int skillId = su.hdSkillId();

		// -----------------------------------------------------
		// 특정 맵 스킬 금지
		// -----------------------------------------------------


		// -----------------------------------------------------
		// 메디테이션 : 비욘드
		// -----------------------------------------------------
		if (skillId == L1SkillId.MEDITATION && pc.hasSkillEffect(L1SkillId.비욘드) && !pc.getAutoHunt()) {
			SC_SystemMessage.send(pc, "\\fY메디테이션:비욘드를 습득하였으므로 사용할 수 없습니다.");
			SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\f3메디테이션:비욘드를 습득하였으므로 사용할 수 없습니다.");
			return false;
		}

		// -----------------------------------------------------
		// 비전 텔레포트 쿨타임
		// -----------------------------------------------------
		if (skillId != 0 && pc.hasSkillEffect(L1SkillId.비전텔레포트)) {
			int sec = pc.getSkillEffectTimeSec(L1SkillId.비전텔레포트);
			SC_SystemMessage.send(pc, String.format("\\fY비전텔레포트 쿨타임으로 인해 %d초 후에 사용가능합니다.", sec));
			return false;
		}

		// -----------------------------------------------------
		// 데몰리션 쿨타임
		// -----------------------------------------------------
		if (skillId != 0 && pc.hasSkillEffect(L1SkillId.데몰리션대기)) {
			int sec = pc.getSkillEffectTimeSec(L1SkillId.데몰리션대기);
			SC_SystemMessage.send(pc, String.format("\\fY데몰리션 쿨타임으로 인해 %d초 후에 사용가능합니다.", sec));
			return false;
		}

		// -----------------------------------------------------
		// 홀리워크 : 에볼루션 (마력의 돌 2개)
		// -----------------------------------------------------
		if (skillId == L1SkillId.HOLY_WALK && pc.hasSkillEffect(L1SkillId.에볼루션)) {
			if (!pc.getInventory().checkItem(40318, 2)) {
				SC_SystemMessage.send(pc, "홀리워크:에볼루션 효과를 받기 위해서는 마력의 돌 2개가 필요합니다.");
				return false;
			}
		}

		// -----------------------------------------------------
		// 무빙 악셀레이션 : 맥시멈 (흑요석 2개)
		// -----------------------------------------------------
		if (skillId == L1SkillId.MOVING_ACCELERATION && pc.hasSkillEffect(L1SkillId.맥시멈)) {
			if (!pc.getInventory().checkItem(40321, 2)) {
				SC_SystemMessage.send(pc, "무빙 악셀레이션:맥시멈 효과를 받기 위해서는 흑요석 2개가 필요합니다.");
				return false;
			}
		}

		// -----------------------------------------------------
		// 패시브로 변형된 스킬
		// -----------------------------------------------------
		if (isPassiveConvertedSkill(skillId)) {
			final String msg = "\\f3패시브로 변형 된 스킬입니다.";
			pc.sendPackets(msg);
			SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, msg);
			return false;
		}

		// -----------------------------------------------------
		// 디스트로이로 대체
		// -----------------------------------------------------
		if (skillId == L1SkillId.FEAR || skillId == L1SkillId.HORROR_OF_DEATH) {
			final String msg = "\\f3디스트로이 스킬로 대체되어서 해당 스킬은 사용할 수 없습니다.";
			pc.sendPackets(msg);
			SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, msg);
			return false;
		}

		// -----------------------------------------------------
		// 루시퍼 직업 제한
		// -----------------------------------------------------
		if (skillId == L1SkillId.LUCIFER && pc.getType() != 4) {
			return false;
		}

		// -----------------------------------------------------
		// 쉐도우 아머 : 데스티니 (흑요석 2개)
		// -----------------------------------------------------
		if (skillId == L1SkillId.SHADOW_ARMOR && pc.hasSkillEffect(L1SkillId.아머데스티니)) {
			if (!pc.getInventory().checkItem(40321, 2)) {
				SC_SystemMessage.send(pc, "쉐도우 아머:데스티니 효과를 받기 위해서는 흑요석 2개가 필요합니다.");
				return false;
			}
			sendArmorDestinyInfo(pc);
		}

		return true;
	}

	// =========================================================
	// 패시브 전환 스킬
	// =========================================================
	private boolean isPassiveConvertedSkill(int skillId) {
		switch (skillId) {
		case L1SkillId.IllUSION_LICH:
		case L1SkillId.IllUSION_DIAMONDGOLEM:
		case L1SkillId.SOLID_CARRIAGE:
		case L1SkillId.PRIDE:
		case L1SkillId.DRESS_EVASION:
		case L1SkillId.BURNING_SPIRIT:
		case L1SkillId.SHINING_ARMOR:
		case L1SkillId.MAJESTY:
		case L1SkillId.GIGANTIC:
			return true;
		default:
			return false;
		}
	}

	// =========================================================
	// 아머 데스티니 안내
	// =========================================================
	private void sendArmorDestinyInfo(L1PcInstance pc) {
		final int level = pc.getLevel();

		// 85레벨부터 적용, 2레벨마다 1% 증가 (최대 10%)
		if (level >= 85) {
			int percent = Math.min(10, (level - 85) / 2 + 5);
			pc.sendPackets(String.format("\\aH현재 레벨 : %d   마법데미지감소: %d%% 적용", level, percent));
		}
	}

	// =========================================================
	// 속성 검사
	// =========================================================
	public boolean checkAttrAgrees(L1SkillUse su) {
		if (su == null || su.exSkill() == null) return false;

		if (su.hdUser() instanceof L1NpcInstance) return true;

		final L1PcInstance pc = su.hdPlayer();
		if (pc == null) return false;

		final int magicAttr = su.exSkill().getAttr();
		if (magicAttr == 0) return true;

		final int lvl = su.exSkill().getSkillLevel();
		if (lvl >= 17 && lvl <= 22) {
			if (!pc.isGm() && magicAttr != pc.getElfAttr()) {
				return false;
			}
		}

		return true;
	}
}