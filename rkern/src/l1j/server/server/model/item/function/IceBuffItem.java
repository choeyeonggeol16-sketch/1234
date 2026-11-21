package l1j.server.server.model.item.function;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;

import l1j.server.server.model.network.core.sc.packet.SC_MpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;


/**
 * 빙수 / 통합버프 아이템 처리.
 * 별도 Controller 없음.
 * 별도 1초 카운트 없음.
 * 기존 L1SkillTimer -> L1SkillStop 종료처리 사용.
 */
public final class IceBuffItem {

	private static final int ITEM_ALL_BUFF = 4100040;
	private static final int ITEM_STR_ICE  = 60208;
	private static final int ITEM_DEX_ICE  = 60209;
	private static final int ITEM_INT_ICE  = 60210;



	private IceBuffItem() {
	}


	/**
	 * @return 해당 클래스에서 처리한 아이템이면 true
	 */
	public static boolean use(L1PcInstance pc, L1ItemInstance item) {
		if (pc == null || item == null) {
			return false;
		}

		switch (item.getItemId()) {
		case ITEM_ALL_BUFF:
			useAllBuff(pc, item);
			return true;

		case ITEM_STR_ICE:
			useStrIce(pc, item);
			return true;

		case ITEM_DEX_ICE:
			useDexIce(pc, item);
			return true;

		case ITEM_INT_ICE:
			useIntIce(pc, item);
			return true;

		default:
			return false;
		}
	}


	// ========================================================================
	// 단일 빙수
	// ========================================================================





	// ========================================================================
	// 통합 버프
	// ========================================================================



	// ========================================================================
	// 실제 효과 적용
	// ========================================================================





	// ========================================================================
	// 기존 효과 정리
	// ========================================================================




    // Existing item behavior merged here; receipt boundary unchanged.


private static final int ALL_TIME_MS = 1800 * 1000;
private static final int[] ALL_BUFF_SKILLS = {
			14, 26, 42, 54, 48, 79, 160,
			206, 211, 216, 4914, 50007, 37
	};

	
	static void useAllBuff(
			L1PcInstance pc,
			L1ItemInstance item) {

		if (pc.getWeapon() == null) {
			pc.sendPackets("무기를 착용하고 사용 가능 합니다.");
			return;
		}

		final boolean bowWeapon = pc.getWeapon().getItem().getType1() == 20;
		ItemReceiptOperations.UseDurableConsume(pc, item, () -> {
		clearIceBuff(pc);
		clearCashScrollBuff(pc);

		removeEffect(pc, L1SkillId.God_buff);

		final int targetId = pc.getId();
		final int targetX = pc.getX();
		final int targetY = pc.getY();

		pc.setBuffnoch(1);

		try {
			for (int i = 0; i < ALL_BUFF_SKILLS.length; i++) {
				L1SkillUse.cast(
						pc,
						ALL_BUFF_SKILLS[i],
						targetId,
						targetX,
						targetY,
						null,
						0,
						L1SkillUse.TYPE_GMBUFF);
			}

			final int iceSkillId;

			if (pc.isElf() && pc.getElfAttr() != 2) {

				applyDexIce(pc, ALL_TIME_MS);

				pc.applySkillModifierFromOwner(L1SkillId.STATUS_CASHSCROLL5, 0);

				pc.setSkillEffect(
						L1SkillId.STATUS_CASHSCROLL5,
						ALL_TIME_MS);

				iceSkillId = L1SkillId.민첩빙수;

			} else if (pc.isWizard()) {

				applyIntIce(pc, ALL_TIME_MS);

				pc.applySkillModifierFromOwner(L1SkillId.STATUS_CASHSCROLL4, 0);

				pc.setSkillEffect(
						L1SkillId.STATUS_CASHSCROLL4,
						ALL_TIME_MS);

				SC_SPMR.send(pc);

				iceSkillId = L1SkillId.지식빙수;

			} else {

				applyStrIce(pc, ALL_TIME_MS);

				pc.applySkillModifierFromOwner(L1SkillId.STATUS_CASHSCROLL6, 0);

				pc.setSkillEffect(
						L1SkillId.STATUS_CASHSCROLL6,
						ALL_TIME_MS);

				iceSkillId = L1SkillId.완력빙수;
			}

			final long ss = SC_SkillSound.pack(
							targetId,
							iceSkillId);

			SC_SkillSound.sendPacked(pc, ss);
			SC_SkillSound.broadcastPacked(pc, ss);

			/*
			 * 무기 타입에 따른 추가 버프.
			 */
			final int weaponSkill = bowWeapon ? 149 : 48;

			L1SkillUse.cast(
					pc,
					weaponSkill,
					targetId,
					targetX,
					targetY,
					null,
					0,
					L1SkillUse.TYPE_GMBUFF);

			pc.curePoison();

		} finally {
			pc.setBuffnoch(0);
		}
		});
	}


    // Existing item behavior merged here; receipt boundary unchanged.


private static final int ICE_TIME_MS = 600 * 1000;

	static void useStrIce(
			L1PcInstance pc,
			L1ItemInstance item) {

		ItemReceiptOperations.UseDurableConsume(pc, item, () -> {
			clearIceBuff(pc);
			applyStrIce(pc, ICE_TIME_MS);
		});
	}



	static void useDexIce(
			L1PcInstance pc,
			L1ItemInstance item) {

		ItemReceiptOperations.UseDurableConsume(pc, item, () -> {
			clearIceBuff(pc);
			applyDexIce(pc, ICE_TIME_MS);
		});
	}



	static void useIntIce(
			L1PcInstance pc,
			L1ItemInstance item) {

		ItemReceiptOperations.UseDurableConsume(pc, item, () -> {
			clearIceBuff(pc);
			applyIntIce(pc, ICE_TIME_MS);
		});
	}


    // Existing item behavior merged here; receipt boundary unchanged.


static void applyStrIce(
			L1PcInstance pc,
			int timeMillis) {

		pc.applySkillModifierFromOwner(L1SkillId.완력빙수, 0);

		pc.setSkillEffect(
				L1SkillId.완력빙수,
				timeMillis);

		SC_OwnCharStatus.send(pc);

		final long ss = SC_SkillSound.pack(
						pc.getId(),
						7954);

		SC_SkillSound.sendPacked(pc, ss);
		SC_SkillSound.broadcastPacked(pc, ss);
	}



	static void applyDexIce(
			L1PcInstance pc,
			int timeMillis) {

		pc.applySkillModifierFromOwner(L1SkillId.민첩빙수, 0);

		pc.setSkillEffect(
				L1SkillId.민첩빙수,
				timeMillis);

		SC_OwnCharStatus.send(pc);

		SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());

		final long ss = SC_SkillSound.pack(
						pc.getId(),
						7952);

		SC_SkillSound.sendPacked(pc, ss);
		SC_SkillSound.broadcastPacked(pc, ss);
	}



	static void applyIntIce(
			L1PcInstance pc,
			int timeMillis) {

		pc.applySkillModifierFromOwner(L1SkillId.지식빙수, 2);

		pc.setSkillEffect(
				L1SkillId.지식빙수,
				timeMillis);

		SC_SPMR.send(pc);

		SC_OwnCharStatus.send(pc);

		SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());

		final long ss = SC_SkillSound.pack(
						pc.getId(),
						7956);

		SC_SkillSound.sendPacked(pc, ss);
		SC_SkillSound.broadcastPacked(pc, ss);
	}


	static void clearIceBuff(
			L1PcInstance pc) {

		removeEffect(
				pc,
				L1SkillId.완력빙수);

		removeEffect(
				pc,
				L1SkillId.민첩빙수);

		removeEffect(
				pc,
				L1SkillId.지식빙수);
	}



	static void clearCashScrollBuff(
			L1PcInstance pc) {

		removeEffect(
				pc,
				L1SkillId.STATUS_CASHSCROLL);

		removeEffect(
				pc,
				L1SkillId.STATUS_CASHSCROLL2);

		removeEffect(
				pc,
				L1SkillId.STATUS_CASHSCROLL3);

		removeEffect(
				pc,
				L1SkillId.STATUS_CASHSCROLL4);

		removeEffect(
				pc,
				L1SkillId.STATUS_CASHSCROLL5);

		removeEffect(
				pc,
				L1SkillId.STATUS_CASHSCROLL6);
	}



	static void removeEffect(
			L1PcInstance pc,
			int skillId) {

		if (pc.hasSkillEffect(skillId)) {
			pc.removeSkillEffect(skillId);
		}
	}

}
