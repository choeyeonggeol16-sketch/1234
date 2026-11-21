package l1j.server.server.model.item.function;

import l1j.server.server.model.character.skill.BraveSpeedEffects;
import l1j.server.MJPassiveSkill.MJPassiveID;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;

import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Item;

import l1j.server.server.model.network.core.sc.packet.SC_SkillBrave;
public class BravePotion extends L1ItemInstance {
    private static final int[] CONFLICTING_BRAVE_SKILLS = {
            1000, 1016, 52, 101, 177, 178, 179, 155, 20079
    };
	public BravePotion(L1Item item) {
		super(item);
	}

	public static void checkConditon(L1PcInstance pc, L1ItemInstance item) {
		if (pc.hasSkillEffect(71)) {
			SC_ServerMessage.send(pc, 698);
			return;
		}
		if ((pc.hasSkillEffect(157)) || (pc.hasSkillEffect(33)) || (pc.hasSkillEffect(10101))
				|| (pc.hasSkillEffect(30007)) || (pc.hasSkillEffect(30002)) || (pc.hasSkillEffect(30006))
				|| (pc.hasSkillEffect(40007)) || (pc.hasSkillEffect(30081)) || (pc.hasSkillEffect(22055))
				|| (pc.hasSkillEffect(22025)) || (pc.hasSkillEffect(22026)) || (pc.hasSkillEffect(22027))
				|| (pc.hasSkillEffect(30004)) || (pc.hasSkillEffect(87)) || (pc.hasSkillEffect(208))
				|| (pc.hasSkillEffect(103)) || (pc.hasSkillEffect(212)) || (pc.hasSkillEffect(123))) {
			return;
		}
		final int itemId = item.getItemId();
		final boolean fruit;
		if (((itemId == 40014) || (itemId == 140014) || (itemId == 41415) || (itemId == 30073))
				&& ((pc.isKnight()) || (pc.isWarrior()))) {
			fruit = false;
		} else if (itemId == 40031 || itemId == 712 || itemId == 30075 && pc.isCrown()) {
			fruit = false;
		} else if (((itemId == 40068) || (itemId == 140068) || (itemId == 210110)) && pc.isElf()) {
			fruit = false;
		} else if (((itemId == 210036) || (itemId == 30077) || (itemId == 713)) && pc.isBlackwizard()) {
			fruit = true;
		} else {
			SC_SystemMessage.send(pc, "아무일도 일어나지 않았습니다.");
			return;
		}
		ItemReceiptOperations.UseDurableConsume(pc, item, () -> {
			pc.cancelAbsoluteBarrier();
			pc.cancelMoebius();
			if (fruit) useFruit(pc, itemId);
			else useBravePotion(pc, itemId);
		});
	}

	private static void useFruit(L1PcInstance pc, int item_id) {
		int time = 0;
		if ((item_id == 210036) || (item_id == 30077)) {
			time = 480;
		}
		if (item_id == 713) {
			time = 1800;
		}
		final int type = pc.isPassive(MJPassiveID.DARK_HORSE.toInt()) ? 3 : 4;
		if (pc.hasSkillEffect(1000)) BraveSpeedEffects.cancelTimer(pc, 1000);
		if (pc.hasSkillEffect(20079)) BraveSpeedEffects.cancelTimer(pc, 20079);
		if (type == 3) pc.setStatusBraveMode(type);
		else pc.setBraveSpeed(type);
		pc.setSkillEffect(type == 3 ? 1000 : 20079, time * 1000);
		SC_SkillBrave.send(pc, pc.getId(), type, time);
		SC_SkillBrave.broadcast(pc, pc.getId(), type, 0);
		SC_SkillSound.send(pc, pc.getId(), pc.isPassive(MJPassiveID.DARK_HORSE.toInt()) ? 751 : 7110);
		SC_SkillSound.broadcast(pc, pc.getId(), pc.isPassive(MJPassiveID.DARK_HORSE.toInt()) ? 751 : 7110);
	}

	private static void useBravePotion(L1PcInstance pc, int item_id) {
		int time = 0;
		if ((item_id == 40014) || (item_id == 30073)) {
			time = 300;
		} else if (item_id == 140014) {
			time = 350;
		} else if (item_id == 41415) {
			time = 1800;
		} else if (item_id == 40031 || item_id == 30075) { // 악마의 피 계열 (군주)
	        time = 600;
	    } else if (item_id == 40068 || item_id == 30076) {
	        time = 480;
	        clearConflictingBraveEffects(pc, 1000);
	    } else if (item_id == 140068) {
	        time = 700;
	        clearConflictingBraveEffects(pc, 1000);
	    } else if (item_id == 210110 || item_id == 712) {
	        time = 1800;
	        clearConflictingBraveEffects(pc, 1000);
	    } else if (item_id == 40733) {
	        time = 600;
	        clearConflictingBraveEffects(pc, 1016);
	    }
		if ((item_id == 40068) || (item_id == 140068) || (item_id == 210110) || (item_id == 30076)) {
			SC_SkillBrave.send(pc, pc.getId(), 3, time);
			SC_SkillBrave.broadcast(pc, pc.getId(), 3, 0);
			pc.setSkillEffect(1016, time * 1000);
			pc.setBraveSpeed(1);
		} else {
			SC_SkillBrave.send(pc, pc.getId(), 1, time);
			SC_SkillBrave.broadcast(pc, pc.getId(), 1, 0);
			pc.setSkillEffect(1000, time * 1000);
			pc.setStatusBraveMode(1);
		}
		SC_SkillSound.send(pc, pc.getId(), 751);
		SC_SkillSound.broadcast(pc, pc.getId(), 751);
	}
	
	private static void clearConflictingBraveEffects(L1PcInstance pc, int excludeSkillId) {
	    for (int skillId : CONFLICTING_BRAVE_SKILLS) {
	        if (skillId != excludeSkillId && pc.hasSkillEffect(skillId)) {
	            BraveSpeedEffects.cancelTimer(pc, skillId);
	        }
	    }
	}
}
