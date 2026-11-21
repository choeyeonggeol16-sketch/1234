package l1j.server.server.model.item.function;


import static l1j.server.server.model.item.function.CreatureItemActions.*;

import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.GameUtils;

public final class ImmuneBladeSkillItems {
    private static final Logger ITEM_USE2_LOG = CreatureItemActions.ITEM_USE2_LOG;
    private ImmuneBladeSkillItems() {}

	public static void UseImmuneBlade(L1PcInstance pc) {

		if (pc.getLevel() >= Config.이뮨투함블레이드레벨) {
			if (GameUtils.isNonAction(pc)) {
				return;
			}
			//			if (pc.hasSkillEffect(L1SkillId.SHOCK_STUN) || pc.hasSkillEffect(L1SkillId.EMPIRE)
			//					|| pc.hasSkillEffect(L1SkillId.EARTH_BIND) || pc.hasSkillEffect(L1SkillId.BONE_BREAK)) {
			//				return;
			//			}

			if (pc.getWeapon() == null) {
				pc.sendPackets("무기를 착용하고 사용 가능 합니다.");
				return;
			}


			if (pc.getType() == 0) { // 자네 군주?
				if (pc.getCurrentHp() < Config.이뮨투함블레이드군주피) {
					pc.sendPackets("HP가 부족합니다.");
					return;
				}
				if (pc.getInventory().getTypeEquipped(1, 2) >= 1 || pc.getInventory().getTypeEquipped(1, 4) >= 1||
						pc.getInventory().getTypeEquipped(1, 5) >= 1 ||pc.getInventory().getTypeEquipped(1, 6) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 7) >= 1 ||pc.getInventory().getTypeEquipped(1, 8) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 10) >= 1 || pc.getInventory().getTypeEquipped(1, 11) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 12) >= 1 ||pc.getInventory().getTypeEquipped(1, 13) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 14) >= 1 ||pc.getInventory().getTypeEquipped(1, 17) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 15) >= 1 ||pc.getInventory().getTypeEquipped(1, 18) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 16) >= 1 ) {
					SC_SystemMessage.send(pc, "사용조건(군주): 한손검 및 양손검 착용");
					return;
				}
			}
			if (pc.getType() == 1) { // 자네 기사?
				if (pc.getCurrentHp() < Config.이뮨투함블레이드기사피) {
					pc.sendPackets("HP가 부족합니다.");
					return;
				}
				if (pc.getInventory().getTypeEquipped(1, 2) >= 1 || pc.getInventory().getTypeEquipped(1, 4) >= 1||
						pc.getInventory().getTypeEquipped(1, 5) >= 1 ||pc.getInventory().getTypeEquipped(1, 6) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 7) >= 1 ||pc.getInventory().getTypeEquipped(1, 8) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 10) >= 1 || pc.getInventory().getTypeEquipped(1, 11) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 12) >= 1 ||pc.getInventory().getTypeEquipped(1, 13) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 14) >= 1 ||pc.getInventory().getTypeEquipped(1, 17) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 15) >= 1 ||pc.getInventory().getTypeEquipped(1, 18) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 16) >= 1 ) {
					SC_SystemMessage.send(pc, "사용조건(기사): 한손검 및 양손검 착용");
					return;
				}
			}
			if (pc.getType() == 2) { // 자네 요정?
				if (pc.getCurrentHp() < Config.이뮨투함블레이드요정피) {
					pc.sendPackets("HP가 부족합니다.");
					return;
				}
				if (pc.getInventory().getTypeEquipped(1, 2) >= 1 || 
						pc.getInventory().getTypeEquipped(1, 5) >= 1 ||pc.getInventory().getTypeEquipped(1, 6) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 7) >= 1 ||pc.getInventory().getTypeEquipped(1, 8) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 10) >= 1 || pc.getInventory().getTypeEquipped(1, 11) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 12) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 14) >= 1 ||pc.getInventory().getTypeEquipped(1, 17) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 15) >= 1 ||pc.getInventory().getTypeEquipped(1, 18) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 16) >= 1 ) {
					SC_SystemMessage.send(pc, "사용조건(요정): 활, 한손검, 양손검 중 착용");
					return;
				}
			}
			if (pc.getType() == 3) { // 자네 마법사?
				if (pc.getCurrentHp() < Config.이뮨투함블레이드법사피) {
					pc.sendPackets("HP가 부족합니다.");
					return;
				}
				if (pc.getInventory().getTypeEquipped(1, 2) >= 1 || pc.getInventory().getTypeEquipped(1, 4) >= 1||
						pc.getInventory().getTypeEquipped(1, 5) >= 1 ||pc.getInventory().getTypeEquipped(1, 6) >= 1 
						||pc.getInventory().getTypeEquipped(1, 8) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 10) >= 1 || pc.getInventory().getTypeEquipped(1, 11) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 12) >= 1 ||pc.getInventory().getTypeEquipped(1, 13) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 14) >= 1 ||pc.getInventory().getTypeEquipped(1, 17) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 15) >= 1 ||pc.getInventory().getTypeEquipped(1, 18) >= 1 
						) {
					SC_SystemMessage.send(pc, "사용조건(마법사): 지팡이, 한손검 중 착용");
					return;
				}
			}
			if (pc.getType() == 4) { // 자네 다크엘프?
				if (pc.getCurrentHp() < Config.이뮨투함블레이드다엘피) {
					pc.sendPackets("HP가 부족합니다.");
					return;
				}
				if (pc.getInventory().getTypeEquipped(1, 1) >= 1 || pc.getInventory().getTypeEquipped(1, 3) >= 1 || pc.getInventory().getTypeEquipped(1, 2) >= 1 || pc.getInventory().getTypeEquipped(1, 4) >= 1||
						pc.getInventory().getTypeEquipped(1, 5) >= 1 ||pc.getInventory().getTypeEquipped(1, 6) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 7) >= 1 ||pc.getInventory().getTypeEquipped(1, 8) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 10) >= 1 || pc.getInventory().getTypeEquipped(1, 13) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 14) >= 1 ||pc.getInventory().getTypeEquipped(1, 17) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 15) >= 1 ||pc.getInventory().getTypeEquipped(1, 18) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 16) >= 1 ) {
					SC_SystemMessage.send(pc, "사용조건(다크엘프): 이도류, 크로우 중 착용");
					return;
				}

			}
			if (pc.getType() == 5) { // 자네 용기사?
				if (pc.getCurrentHp() < Config.이뮨투함블레이드용기사피) {
					pc.sendPackets("HP가 부족합니다.");
					return;
				}
				if (pc.getInventory().getTypeEquipped(1, 2) >= 1 || pc.getInventory().getTypeEquipped(1, 4) >= 1||
						pc.getInventory().getTypeEquipped(1, 5) >= 1 ||pc.getInventory().getTypeEquipped(1, 6) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 7) >= 1 ||pc.getInventory().getTypeEquipped(1, 8) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 10) >= 1 || pc.getInventory().getTypeEquipped(1, 11) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 12) >= 1 ||pc.getInventory().getTypeEquipped(1, 13) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 14) >= 1 ||pc.getInventory().getTypeEquipped(1, 17) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 15) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 16) >= 1 ) {
					SC_SystemMessage.send(pc, "사용조건(용기사): 체인소드, 한손검, 양손검 중 착용");
					return;
				}
			}
			if (pc.getType() == 6) { // 자네 환술사?
				if (pc.getCurrentHp() < Config.이뮨투함블레이드환술피) {
					pc.sendPackets("HP가 부족합니다.");
					return;
				}
				if (pc.getInventory().getTypeEquipped(1, 1) >= 1 ||pc.getInventory().getTypeEquipped(1, 3) >= 1 ||pc.getInventory().getTypeEquipped(1, 18) >= 1 ||pc.getInventory().getTypeEquipped(1, 2) >= 1 || pc.getInventory().getTypeEquipped(1, 4) >= 1||
						pc.getInventory().getTypeEquipped(1, 5) >= 1 ||pc.getInventory().getTypeEquipped(1, 6) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 7) >= 1 ||pc.getInventory().getTypeEquipped(1, 8) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 10) >= 1 || pc.getInventory().getTypeEquipped(1, 11) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 12) >= 1 ||pc.getInventory().getTypeEquipped(1, 13) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 14) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 15) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 16) >= 1 ) {
					SC_SystemMessage.send(pc, "사용조건(환술사): 키링크 착용");
					return;
				}
			}
			if (pc.getType() == 7) { // 자네 환술사?
				if (pc.getCurrentHp() < Config.이뮨투함블레이드전사피) {
					pc.sendPackets("HP가 부족합니다.");
					return;
				}
				if (pc.getInventory().getTypeEquipped(1, 1) >= 1 ||pc.getInventory().getTypeEquipped(1, 3) >= 1 ||pc.getInventory().getTypeEquipped(1, 18) >= 1 ||pc.getInventory().getTypeEquipped(1, 2) >= 1 || pc.getInventory().getTypeEquipped(1, 4) >= 1||
						pc.getInventory().getTypeEquipped(1, 5) >= 1 ||pc.getInventory().getTypeEquipped(1, 18) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 7) >= 1 ||pc.getInventory().getTypeEquipped(1, 8) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 10) >= 1 || pc.getInventory().getTypeEquipped(1, 11) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 12) >= 1 ||pc.getInventory().getTypeEquipped(1, 13) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 14) >= 1 ||

						pc.getInventory().getTypeEquipped(1, 16) >= 1 ) {
					SC_SystemMessage.send(pc, "사용조건(전사): 도끼 착용");
					return;
				}
			}
			if (pc.hasSkillEffect(L1SkillId.이뮨투함블레이드대기)) {
				//
				int n = pc.getSkillEffectTimeSec(L1SkillId.이뮨투함블레이드대기);
				SC_SystemMessage.send(pc, "\\fY이뮨 투 함 블레이드 : " + n + "초 후에 사용가능합니다.");
				return;
			}



			if (pc.getType() == 0) { // 자네 군주?
				pc.setCurrentHp(pc.getCurrentHp() - Config.이뮨투함블레이드군주피);
			} if (pc.getType() == 1) { // 자네 기사?
				pc.setCurrentHp(pc.getCurrentHp() - Config.이뮨투함블레이드기사피);
			} if ( pc.getType() == 2) { // 자네 요정?
				pc.setCurrentHp(pc.getCurrentHp() - Config.이뮨투함블레이드요정피);
			} if ( pc.getType() == 3) { // 자네 마법사?
				pc.setCurrentHp(pc.getCurrentHp() - Config.이뮨투함블레이드법사피);
			} if ( pc.getType() == 4) { // 자네 다크엘프?
				pc.setCurrentHp(pc.getCurrentHp() - Config.이뮨투함블레이드다엘피);
			} if (pc.getType() == 5) { // 자네 용기사?
				pc.setCurrentHp(pc.getCurrentHp() - Config.이뮨투함블레이드용기사피);
			} if (pc.getType() == 6) { // 자네 환술사?
				pc.setCurrentHp(pc.getCurrentHp() - Config.이뮨투함블레이드환술피);
			} if (pc.getType() == 7) { // 자네 환술사?
				pc.setCurrentHp(pc.getCurrentHp() - Config.이뮨투함블레이드전사피);
			}

			if (pc.hasSkillEffect(L1SkillId.이뮨투함블레이드)) {
				pc.removeSkillEffect(L1SkillId.이뮨투함블레이드);
			}
			
			L1SkillUse.cast(pc, L1SkillId.보조버프, pc.getId(), pc.getX(),
					pc.getY(), null, 0, L1SkillUse.TYPE_SPELLSC);
			
			SC_SkillSound.send(pc, pc.getId(), 16518);
			SC_SkillSound.broadcast(pc, pc.getId(), 16518);
			pc.setSkillEffect(L1SkillId.이뮨투함블레이드, Config.이뮨투함블레이드지속 * 1000);
			pc.setSkillEffect(L1SkillId.이뮨투함블레이드대기, Config.이뮨투함블레이드대기 * 1000);
			SC_InventoryIcon.icoNew(pc, L1SkillId.이뮨투함블레이드, 5125, Config.이뮨투함블레이드지속, true);
			pc.sendPackets("HP를 소모하며 이뮨 투 함 블레이드가 효과가 적용됩니다.");

		} else {
			pc.sendPackets("현재 [이뮨 투 함 블레이드]를 사용할 수 없는 조건입니다.");

		}

	}
}
