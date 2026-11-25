package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_HpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_MpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI.eNotiType;
import l1j.server.server.utils.eKind;
import static l1j.server.server.model.item.function.CreatureItemActions.*;

/** One deterministic doll-potential removal sequence, without per-effect helper objects. */
final class DollPotentialRemoval {
    private DollPotentialRemoval() { }

    static void remove(L1PcInstance pc) {
        removeVitalDefense(pc);
        removeAttributes(pc);
        removeCombat(pc);
        removeResistances(pc);
    }

    /** Original END notification layout and bit mask; emitted after each stat change. */
    private static void sendEnd(L1PcInstance pc, int skillId) {
        SC_SPELL_BUFF_NOTI.sendFields(pc, eNotiType.END, skillId, 0, null, 0, 976,
                0, 0, 0, 0, true, 0, 0, 0, 1063);
    }

    private static void removeVitalDefense(L1PcInstance pc) {
		if(pc.hasSkillEffect(L1SkillId.hp300)) {
			pc.removeSkillEffect(L1SkillId.hp300);
			pc.addMaxHp(-300);	
			SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
			SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
            sendEnd(pc, L1SkillId.hp300);
			SC_OwnCharAttrDef.send(pc);

		}

		if(pc.hasSkillEffect(L1SkillId.mp300)) {
			pc.removeSkillEffect(L1SkillId.mp300);
			pc.addMaxMp(-300);	
			SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
			SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
            sendEnd(pc, L1SkillId.mp300);
			SC_OwnCharAttrDef.send(pc);

		}

		if(pc.hasSkillEffect(L1SkillId.대미지감소5)) {
			pc.removeSkillEffect(L1SkillId.대미지감소5);
			pc.addDamageReductionByArmor(-5);
            sendEnd(pc, L1SkillId.대미지감소5);
			SC_OwnCharAttrDef.send(pc);

		}

		if(pc.hasSkillEffect(L1SkillId.ac5)) {
			pc.removeSkillEffect(L1SkillId.ac5);
			pc.addAc(5);
            sendEnd(pc, L1SkillId.ac5);
			SC_OwnCharAttrDef.send(pc);
		}

		if(pc.hasSkillEffect(L1SkillId.mr15)) {
			pc.removeSkillEffect(L1SkillId.mr15);
			pc.AddMDefend(-15);
            sendEnd(pc, L1SkillId.mr15);
			SC_OwnCharAttrDef.send(pc);
			SC_SPMR.send(pc);
		}

		if(pc.hasSkillEffect(L1SkillId.경험치10)) {
			pc.removeSkillEffect(L1SkillId.경험치10);
            sendEnd(pc, L1SkillId.경험치10);
			SC_OwnCharAttrDef.send(pc);
		}

		if(pc.hasSkillEffect(L1SkillId.모든속성저항20)) {
			pc.removeSkillEffect(L1SkillId.모든속성저항20);
			pc.AddEarthResist(-20);
			pc.AddWindResist(-20);
			pc.AddWaterResist(-20);
			pc.AddFireResist(-20);
            sendEnd(pc, L1SkillId.모든속성저항20);
			SC_OwnCharAttrDef.send(pc);
		}
    
    }

    private static void removeAttributes(L1PcInstance pc) {
		if(pc.hasSkillEffect(L1SkillId.힘3)) {
			pc.removeSkillEffect(L1SkillId.힘3);
			pc.AddStatBonusStr((byte) -3);
            sendEnd(pc, L1SkillId.힘3);
			SC_OwnCharAttrDef.send(pc);
		}

		if(pc.hasSkillEffect(L1SkillId.덱3)) {
			pc.removeSkillEffect(L1SkillId.덱3);
			pc.AddStatBonusDex((byte) -3);
            sendEnd(pc, L1SkillId.덱3);
			SC_OwnCharAttrDef.send(pc);
		}

		if(pc.hasSkillEffect(L1SkillId.콘3)) {
			pc.removeSkillEffect(L1SkillId.콘3);
			pc.AddStatBonusCon((byte) -3);
            sendEnd(pc, L1SkillId.콘3);
			SC_OwnCharAttrDef.send(pc);
		}

		if(pc.hasSkillEffect(L1SkillId.위즈3)) {
			pc.removeSkillEffect(L1SkillId.위즈3);
			pc.AddStatBonusWis((byte) -3);
            sendEnd(pc, L1SkillId.위즈3);
			SC_OwnCharAttrDef.send(pc);
		}

		if(pc.hasSkillEffect(L1SkillId.인트3)) {
			pc.removeSkillEffect(L1SkillId.인트3);
			pc.AddStatBonusInt((byte) -3);
            sendEnd(pc, L1SkillId.인트3);
			SC_OwnCharAttrDef.send(pc);
		}
    
    }

    private static void removeCombat(L1PcInstance pc) {
		if(pc.hasSkillEffect(L1SkillId.pvp대미지7)) {
			pc.removeSkillEffect(L1SkillId.pvp대미지7);
			pc.AddPvpDamage(-7);
            sendEnd(pc, L1SkillId.pvp대미지7);
			SC_OwnCharAttrDef.send(pc);
		}

		if(pc.hasSkillEffect(L1SkillId.pvp감소7)) {
			pc.removeSkillEffect(L1SkillId.pvp감소7);
			pc.addPVPMagicDamageReduction(-7);
            sendEnd(pc, L1SkillId.pvp감소7);
			SC_OwnCharAttrDef.send(pc);
		}

		if(pc.hasSkillEffect(L1SkillId.근거리대미지5)) {
			pc.removeSkillEffect(L1SkillId.근거리대미지5);
			pc.addDmgup(-5);
            sendEnd(pc, L1SkillId.근거리대미지5);
			SC_OwnCharAttrDef.send(pc);
		}

		if(pc.hasSkillEffect(L1SkillId.원거리대미지5)) {
			pc.removeSkillEffect(L1SkillId.원거리대미지5);
			pc.addBowDmgup(-5);
            sendEnd(pc, L1SkillId.원거리대미지5);
			SC_OwnCharAttrDef.send(pc);
		}

		if(pc.hasSkillEffect(L1SkillId.근거리명중5)) {
			pc.removeSkillEffect(L1SkillId.근거리명중5);
			pc.addHitup(-5);
            sendEnd(pc, L1SkillId.근거리명중5);
			SC_OwnCharAttrDef.send(pc);
		}

		if(pc.hasSkillEffect(L1SkillId.원거리명중5)) {
			pc.removeSkillEffect(L1SkillId.원거리명중5);
			pc.addBowHitup(-5);
            sendEnd(pc, L1SkillId.원거리명중5);
			SC_OwnCharAttrDef.send(pc);
		}

		if(pc.hasSkillEffect(L1SkillId.sp5)) {
			pc.removeSkillEffect(L1SkillId.sp5);
			pc.AddStatSp(-5);
            sendEnd(pc, L1SkillId.sp5);
			SC_OwnCharAttrDef.send(pc);
		}

		if(pc.hasSkillEffect(L1SkillId.마법적중5)) {
			pc.removeSkillEffect(L1SkillId.마법적중5);
			pc.addMagicHit(-5);
            sendEnd(pc, L1SkillId.마법적중5);
			SC_OwnCharAttrDef.send(pc);
		}
    
    }

    private static void removeResistances(L1PcInstance pc) {
		if(pc.hasSkillEffect(L1SkillId.기술내성8)) {
			pc.removeSkillEffect(L1SkillId.기술내성8);
			pc.AddSpecialResistance(eKind.ABILITY, -8);
            sendEnd(pc, L1SkillId.기술내성8);
			SC_OwnCharAttrDef.send(pc);
		}

		if(pc.hasSkillEffect(L1SkillId.정령내성8)) {
			pc.removeSkillEffect(L1SkillId.정령내성8);
			pc.AddSpecialResistance(eKind.SPIRIT, -8);
            sendEnd(pc, L1SkillId.정령내성8);
			SC_OwnCharAttrDef.send(pc);
		}

		if(pc.hasSkillEffect(L1SkillId.용언내성3)) {
			pc.removeSkillEffect(L1SkillId.용언내성3);
			pc.AddSpecialResistance(eKind.DRAGON_SPELL, -3);
            sendEnd(pc, L1SkillId.용언내성3);
			SC_OwnCharAttrDef.send(pc);
		}

		if(pc.hasSkillEffect(L1SkillId.공포내성3)) {
			pc.removeSkillEffect(L1SkillId.공포내성3);
			pc.AddSpecialResistance(eKind.FEAR, -3);
            sendEnd(pc, L1SkillId.공포내성3);
			SC_OwnCharAttrDef.send(pc);
		}

		if(pc.hasSkillEffect(L1SkillId.모든내성3)) {
			pc.removeSkillEffect(L1SkillId.모든내성3);
			pc.AddSpecialResistance(eKind.ALL, -3);
            sendEnd(pc, L1SkillId.모든내성3);
			SC_OwnCharAttrDef.send(pc);
		}

		if(pc.hasSkillEffect(L1SkillId.정령적중5)) {
			pc.removeSkillEffect(L1SkillId.정령적중5);
			pc.AddSpecialPierce(eKind.SPIRIT, -5);
            sendEnd(pc, L1SkillId.정령적중5);
			SC_OwnCharAttrDef.send(pc);
		}

		if(pc.hasSkillEffect(L1SkillId.용언적중5)) {
			pc.removeSkillEffect(L1SkillId.용언적중5);
			pc.AddSpecialPierce(eKind.DRAGON_SPELL, -5);
            sendEnd(pc, L1SkillId.용언적중5);
			SC_OwnCharAttrDef.send(pc);
		}

		if(pc.hasSkillEffect(L1SkillId.공포적중5)) {
			pc.removeSkillEffect(L1SkillId.공포적중5);
			pc.AddSpecialPierce(eKind.FEAR, -5);
            sendEnd(pc, L1SkillId.공포적중5);
			SC_OwnCharAttrDef.send(pc);
		}

		if(pc.hasSkillEffect(L1SkillId.기술적중5)) {
			pc.removeSkillEffect(L1SkillId.기술적중5);
			pc.AddSpecialPierce(eKind.ABILITY, -5);
            sendEnd(pc, L1SkillId.기술적중5);
			SC_OwnCharAttrDef.send(pc);
		}

		if(pc.hasSkillEffect(L1SkillId.모든적중3)) {
			pc.removeSkillEffect(L1SkillId.모든적중3);
			pc.AddSpecialPierce(eKind.ALL, -3);
            sendEnd(pc, L1SkillId.모든적중3);
			SC_OwnCharAttrDef.send(pc);
		}

		if(pc.hasSkillEffect(L1SkillId.경험치보너스20)) {
			pc.removeSkillEffect(L1SkillId.경험치보너스20);
            sendEnd(pc, L1SkillId.경험치보너스20);
			SC_OwnCharAttrDef.send(pc);
		}
    
    }
}
