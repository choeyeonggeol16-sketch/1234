package l1j.server.server.model.item.function;

import l1j.server.server.model.character.skill.BraveSpeedEffects;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.MovementSpeedEffects;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillBrave;
import l1j.server.server.model.network.core.sc.packet.SC_SkillHaste;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import static l1j.server.server.model.character.skill.L1SkillId.DANCING_BLADES;
import static l1j.server.server.model.character.skill.L1SkillId.DECAY_POTION;
import static l1j.server.server.model.character.skill.L1SkillId.FOCUS_WAVE;
import static l1j.server.server.model.character.skill.L1SkillId.GREATER_HASTE;
import static l1j.server.server.model.character.skill.L1SkillId.HASTE;
import static l1j.server.server.model.character.skill.L1SkillId.HOLY_WALK;
import static l1j.server.server.model.character.skill.L1SkillId.HURRICANE;
import static l1j.server.server.model.character.skill.L1SkillId.MOVING_ACCELERATION;
import static l1j.server.server.model.character.skill.L1SkillId.SAND_STORM;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_BRAVE;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_ELFBRAVE;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_FRUIT;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_HASTE;
import static l1j.server.server.model.item.function.CreatureItemActions.cancelAbsoluteBarrier;

final class MovementPotionEffects {
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(MovementPotionEffects.class.getName());
    private MovementPotionEffects() {}

    // Existing potion effect implementation.


public static void useGreenPotion(L1PcInstance pc, int itemId) {
		if (pc.hasSkillEffect(71) == true) { // 디케이포션 상태
			sendMessage(pc, 698); // \f1마력에 의해 아무것도 마실 수가
			// 없습니다.
			return;
		}

		// 아브소르트바리아의 해제
		cancelAbsoluteBarrier(pc);

		int time = 0;
		if (itemId == L1ItemId.POTION_OF_HASTE_SELF || itemId == 7006) { // 그린
			time = 300;
		} else if (itemId == L1ItemId.B_POTION_OF_HASTE_SELF) { // 축복된 그린 일부
			time = 350;
		} else if (itemId == 40018 || itemId == 41342) { // 강화 그린 일부, 축복된 와인,
			time = 1800;
		} else if (itemId == 140018 || itemId == 41338 || itemId == 30158) { // 축복된
			time = 2250;
		} else if (itemId == 40039) { // 와인
			time = 600;
		} else if (itemId == 40040) { // 위스키
			time = 900;
		} else if (itemId == 30067) {
			time = 300;
		} else if (itemId == 40030) { // 상아의 탑의 헤이 파업 일부
			time = 1800;
		} else if (itemId == 41261 || itemId == 41262 || itemId == 41268 || itemId == 41269 || itemId == 41271
				|| itemId == 41272 || itemId == 41273) {
			time = 30;
		}

		sendSound(pc, pc.getId(), 191);
		broadcastSound(pc, pc.getId(), 191);
		sendMessage(pc, 183);
		// XXX:헤이스트아이템 장비시, 취한 상태가 해제되는지 불명
		if (pc.getHasteItemEquipped() > 0) {
			return;
		}
		// 취한 상태를 해제
		pc.setDrink(false);

		// 헤이 파업, 그레이터 헤이 파업과는 중복 하지 않는다
		if (pc.hasSkillEffect(HASTE)) {
			pc.killSkillEffectTimer(HASTE);
			MovementSpeedEffects.endHaste(pc, HASTE);
		} else if (pc.hasSkillEffect(GREATER_HASTE)) {
			pc.killSkillEffectTimer(GREATER_HASTE);
			MovementSpeedEffects.endHaste(pc, GREATER_HASTE);
		} else if (pc.hasSkillEffect(STATUS_HASTE)) {
			int currentTime = 0;
			currentTime = pc.getSkillEffectTimeSec(STATUS_HASTE);
			if (currentTime >= 7200) {
				time = 7200;
			} else {
				time += currentTime;
			}
		}

		// 슬로우, 그레이터 슬로우, 엔탕르중은 슬로우 상태를 해제할 뿐
		if (!MovementSpeedEffects.cancelSlowWithPotion(pc)) {
			sendHaste(pc, pc.getId(), 1, time >= 7200 ? 7200 : time);
			broadcastHaste(pc, pc.getId(), 1, 0);
			pc.setMoveSpeed(1);
			pc.setSkillEffect(STATUS_HASTE, (time >= 7200 ? 7200 : time) * 1000);
		}
	}


    // Existing potion effect implementation.


public static void useBravePotion(L1PcInstance pc, int item_id) {
		if (pc.hasSkillEffect(71) == true) { // 디케이포션 상태
			sendMessage(pc, 698);// \f1마력에 의해 아무것도 마실 수가
			// 없습니다.
			return;
		}
		// 아브소르트바리아의 해제
		cancelAbsoluteBarrier(pc);

		int time = 0;
		if (item_id == L1ItemId.POTION_OF_EMOTION_BRAVERY || item_id == 30073) { // 치우침
			// 이브
			// 일부
			time = 300;
		} else if (item_id == L1ItemId.B_POTION_OF_EMOTION_BRAVERY) { // 축복된
			// 치우침이브
			// 일부
			time = 350;
		} else if (item_id == 41415) { // 강화 치우침 이브 일부
			time = 1800;
		} else if (item_id == 712) {
			time = 1800;
		} else if (item_id == 40068 || item_id == 30076) { // 에르브왓훌
			time = 480;
			if (pc.hasSkillEffect(STATUS_BRAVE)) { // 용기와는 중복 하지 않는다.
				BraveSpeedEffects.cancelTimer(pc, STATUS_BRAVE);
			}
			pc.remove_elf_second_brave();
		} else if (item_id == 140068) { // 축복된 에르브왓훌
			time = 700;
			if (pc.hasSkillEffect(STATUS_BRAVE)) { // 용기 효과와는 중복 하지 않는다.
				BraveSpeedEffects.cancelTimer(pc, STATUS_BRAVE);
			}
			pc.remove_elf_second_brave();
		} else if (item_id == 210110) { // 복지 엘븐와퍼
			time = 1800;
			if (pc.hasSkillEffect(STATUS_BRAVE)) { // 용기 효과와는 중복 하지 않는다.
				BraveSpeedEffects.cancelTimer(pc, STATUS_BRAVE);
			}

			if (pc.hasSkillEffect(FOCUS_WAVE)) {
				BraveSpeedEffects.cancelTimer(pc, FOCUS_WAVE);
			}
			if (pc.hasSkillEffect(HURRICANE)) {
				BraveSpeedEffects.cancelTimer(pc, HURRICANE);
			}
			if (pc.hasSkillEffect(SAND_STORM)) {
				BraveSpeedEffects.cancelTimer(pc, SAND_STORM);
			}
			if (pc.hasSkillEffect(DANCING_BLADES)) { // 댄싱하고는 중복하지않는다
				BraveSpeedEffects.cancelTimer(pc, DANCING_BLADES);
			}
		} else if (item_id == 40031 || item_id == 30075) { // 이비르브랏드
			time = 600;
		} else if (item_id == 40733) { // 명예의 코인
			time = 600;
			if (pc.hasSkillEffect(STATUS_ELFBRAVE)) {
				BraveSpeedEffects.cancelTimer(pc, STATUS_ELFBRAVE);
			}
			if (pc.hasSkillEffect(HOLY_WALK)) { // 호-리 워크와는 중복 하지 않는다
				BraveSpeedEffects.cancelTimer(pc, HOLY_WALK);
			}
			if (pc.hasSkillEffect(MOVING_ACCELERATION)) { // 무빙 악 세레이션과는 중복 하지
				// 않는다
				BraveSpeedEffects.cancelTimer(pc, MOVING_ACCELERATION);
			}
			if (pc.hasSkillEffect(STATUS_FRUIT)) { // 유그드라열매와는 중복안됨
				BraveSpeedEffects.cancelTimer(pc, STATUS_FRUIT);
			}
		}

		if (item_id == 40068 || item_id == 140068 || item_id == 210110 || item_id == 30076) { // 엘븐
			// 와퍼
			sendBrave(pc, pc.getId(), 3, time);
			broadcastBrave(pc, pc.getId(), 3, 0);
			pc.setSkillEffect(STATUS_ELFBRAVE, time * 1000);
			pc.setBraveSpeed(1);
		} else {
			sendBrave(pc, pc.getId(), 1, time);
			broadcastBrave(pc, pc.getId(), 1, 0);
			pc.setSkillEffect(STATUS_BRAVE, time * 1000);
			pc.setStatusBraveMode(1);
		}
		sendSound(pc, pc.getId(), 751);
		broadcastSound(pc, pc.getId(), 751);
	}


    // Existing potion effect implementation.


public static void useFruit(L1PcInstance pc, int item_id) {
		if (pc.hasSkillEffect(DECAY_POTION) == true) {
			sendMessage(pc, 698);
			return;
		}
		cancelAbsoluteBarrier(pc);

		int time = 0;
		if (item_id == 210036 || item_id == 30077) {
			time = 480;
		}
		if (item_id == 713) {
			time = 1800;
		}
		if (pc.hasSkillEffect(L1SkillId.STATUS_BRAVE)) {
			BraveSpeedEffects.cancelTimer(pc, L1SkillId.STATUS_BRAVE);
		}


		pc.setSkillEffect(STATUS_FRUIT, time * 1000);
		//TODO 다크호스 추가
		sendBrave(pc, pc.getId(), pc.getBraveSpeed(), time);
		broadcastBrave(pc, pc.getId(), pc.getBraveSpeed(), 0);
		sendSound(pc, pc.getId(), 7110);
		broadcastSound(pc, pc.getId(), 7110);

	}


    // Each output is optional presentation. Never throw across a committed debit.
    private static void noticeFailure(String packet, RuntimeException failure) {
        LOG.log(java.util.logging.Level.WARNING, "Movement potion " + packet + " delivery failed", failure);
    }
    private static void sendMessage(L1PcInstance pc, int message) {
        try { SC_ServerMessage.send(pc, message); }
        catch (RuntimeException e) { noticeFailure("message", e); }
    }
    private static void sendSound(L1PcInstance pc, int id, int sound) {
        try { SC_SkillSound.send(pc, id, sound); }
        catch (RuntimeException e) { noticeFailure("sound", e); }
    }
    private static void broadcastSound(L1PcInstance pc, int id, int sound) {
        try { SC_SkillSound.broadcast(pc, id, sound); }
        catch (RuntimeException e) { noticeFailure("sound broadcast", e); }
    }
    private static void sendHaste(L1PcInstance pc, int id, int type, int time) {
        try { SC_SkillHaste.send(pc, id, type, time); }
        catch (RuntimeException e) { noticeFailure("haste", e); }
    }
    private static void broadcastHaste(L1PcInstance pc, int id, int type, int time) {
        try { SC_SkillHaste.broadcast(pc, id, type, time); }
        catch (RuntimeException e) { noticeFailure("haste broadcast", e); }
    }
    private static void sendBrave(L1PcInstance pc, int id, int type, int time) {
        try { SC_SkillBrave.send(pc, id, type, time); }
        catch (RuntimeException e) { noticeFailure("brave", e); }
    }
    private static void broadcastBrave(L1PcInstance pc, int id, int type, int time) {
        try { SC_SkillBrave.broadcast(pc, id, type, time); }
        catch (RuntimeException e) { noticeFailure("brave broadcast", e); }
    }
}
