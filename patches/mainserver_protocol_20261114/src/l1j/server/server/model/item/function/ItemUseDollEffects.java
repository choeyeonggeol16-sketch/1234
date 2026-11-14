package l1j.server.server.model.item.function;
import l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI;
import l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;

import l1j.server.server.model.network.core.sc.packet.SC_MpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_HpUpdate;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;



import l1j.server.server.utils.eKind;



import static l1j.server.server.model.item.function.ItemUseCreature.*;

public final class ItemUseDollEffects {
    private ItemUseDollEffects() {}
	public static void RemoveDollPotential(L1PcInstance pc) {
		if(pc.hasSkillEffect(L1SkillId.hp300)) {
			pc.removeSkillEffect(L1SkillId.hp300);
			pc.addMaxHp(-300);	
			SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
			SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
			l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
			int notiWire_spellId = 0;
			int notiWire_duration = 0;
			int notiWire_offIconId = 0;
			boolean notiWire_isGood = false;
			int notiWire_bit = 0;


			{ notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
			{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.hp300; }
			{ notiWire_bit |= 4; notiWire_duration = 0; }
			{ notiWire_bit |= 32; notiWire_offIconId = 976; }
			{ notiWire_bit |= 1024; notiWire_isGood = true; }
			SC_SPELL_BUFF_NOTI.sendFields(
			        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0, 0,
			        notiWire_isGood, 0, 0, 0, notiWire_bit);
			SC_OwnCharAttrDef.send(pc);

		}
		if(pc.hasSkillEffect(L1SkillId.mp300)) {
			pc.removeSkillEffect(L1SkillId.mp300);
			pc.addMaxMp(-300);	
			SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
			SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
			l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
			int notiWire_spellId = 0;
			int notiWire_duration = 0;
			int notiWire_offIconId = 0;
			boolean notiWire_isGood = false;
			int notiWire_bit = 0;


			{ notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
			{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.mp300; }
			{ notiWire_bit |= 4; notiWire_duration = 0; }
			{ notiWire_bit |= 32; notiWire_offIconId = 976; }
			{ notiWire_bit |= 1024; notiWire_isGood = true; }
			SC_SPELL_BUFF_NOTI.sendFields(
			        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0, 0,
			        notiWire_isGood, 0, 0, 0, notiWire_bit);
			SC_OwnCharAttrDef.send(pc);

		}
		if(pc.hasSkillEffect(L1SkillId.대미지감소5)) {
			pc.removeSkillEffect(L1SkillId.대미지감소5);
			pc.addDamageReductionByArmor(-5);	
			l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
			int notiWire_spellId = 0;
			int notiWire_duration = 0;
			int notiWire_offIconId = 0;
			boolean notiWire_isGood = false;
			int notiWire_bit = 0;


			{ notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
			{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.대미지감소5; }
			{ notiWire_bit |= 4; notiWire_duration = 0; }
			{ notiWire_bit |= 32; notiWire_offIconId = 976; }
			{ notiWire_bit |= 1024; notiWire_isGood = true; }
					SC_SPELL_BUFF_NOTI.sendFields(
					        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0,
					        0, 0, notiWire_isGood, 0, 0, 0, notiWire_bit);
			SC_OwnCharAttrDef.send(pc);

		}
		if(pc.hasSkillEffect(L1SkillId.ac5)) {
			pc.removeSkillEffect(L1SkillId.ac5);
			pc.getAC().addAc(5);
			l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
			int notiWire_spellId = 0;
			int notiWire_duration = 0;
			int notiWire_offIconId = 0;
			boolean notiWire_isGood = false;
			int notiWire_bit = 0;


			{ notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
			{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.ac5; }
			{ notiWire_bit |= 4; notiWire_duration = 0; }
			{ notiWire_bit |= 32; notiWire_offIconId = 976; }
			{ notiWire_bit |= 1024; notiWire_isGood = true; }
			SC_SPELL_BUFF_NOTI.sendFields(
			        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0, 0,
			        notiWire_isGood, 0, 0, 0, notiWire_bit);
			SC_OwnCharAttrDef.send(pc);
		}
		if(pc.hasSkillEffect(L1SkillId.mr15)) {
			pc.removeSkillEffect(L1SkillId.mr15);
			pc.getResistance().addMr(-15);
			l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
			int notiWire_spellId = 0;
			int notiWire_duration = 0;
			int notiWire_offIconId = 0;
			boolean notiWire_isGood = false;
			int notiWire_bit = 0;


			{ notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
			{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.mr15; }
			{ notiWire_bit |= 4; notiWire_duration = 0; }
			{ notiWire_bit |= 32; notiWire_offIconId = 976; }
			{ notiWire_bit |= 1024; notiWire_isGood = true; }
			SC_SPELL_BUFF_NOTI.sendFields(
			        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0, 0,
			        notiWire_isGood, 0, 0, 0, notiWire_bit);
			SC_OwnCharAttrDef.send(pc);
			SC_SPMR.send(pc);
		}
		if(pc.hasSkillEffect(L1SkillId.경험치10)) {
			pc.removeSkillEffect(L1SkillId.경험치10);
			l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
			int notiWire_spellId = 0;
			int notiWire_duration = 0;
			int notiWire_offIconId = 0;
			boolean notiWire_isGood = false;
			int notiWire_bit = 0;


			{ notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
			{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.경험치10; }
			{ notiWire_bit |= 4; notiWire_duration = 0; }
			{ notiWire_bit |= 32; notiWire_offIconId = 976; }
			{ notiWire_bit |= 1024; notiWire_isGood = true; }
			SC_SPELL_BUFF_NOTI.sendFields(
			        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0, 0,
			        notiWire_isGood, 0, 0, 0, notiWire_bit);
			SC_OwnCharAttrDef.send(pc);
		}
		if(pc.hasSkillEffect(L1SkillId.모든속성저항20)) {
			pc.removeSkillEffect(L1SkillId.모든속성저항20);
			pc.getResistance().addEarth(-20);
			pc.getResistance().addWind(-20);
			pc.getResistance().addWater(-20);
			pc.getResistance().addFire(-20);	
			l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
			int notiWire_spellId = 0;
			int notiWire_duration = 0;
			int notiWire_offIconId = 0;
			boolean notiWire_isGood = false;
			int notiWire_bit = 0;


			{ notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
			{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.모든속성저항20; }
			{ notiWire_bit |= 4; notiWire_duration = 0; }
			{ notiWire_bit |= 32; notiWire_offIconId = 976; }
			{ notiWire_bit |= 1024; notiWire_isGood = true; }
			SC_SPELL_BUFF_NOTI.sendFields(
			        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0, 0,
			        notiWire_isGood, 0, 0, 0, notiWire_bit);
			SC_OwnCharAttrDef.send(pc);
		}
		if(pc.hasSkillEffect(L1SkillId.힘3)) {
			pc.removeSkillEffect(L1SkillId.힘3);
			pc.getAbility().addAddedStr((byte) -3);
			l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
			int notiWire_spellId = 0;
			int notiWire_duration = 0;
			int notiWire_offIconId = 0;
			boolean notiWire_isGood = false;
			int notiWire_bit = 0;


			{ notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
			{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.힘3; }
			{ notiWire_bit |= 4; notiWire_duration = 0; }
			{ notiWire_bit |= 32; notiWire_offIconId = 976; }
			{ notiWire_bit |= 1024; notiWire_isGood = true; }
			SC_SPELL_BUFF_NOTI.sendFields(
			        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0, 0,
			        notiWire_isGood, 0, 0, 0, notiWire_bit);
			SC_OwnCharAttrDef.send(pc);
		}
		if(pc.hasSkillEffect(L1SkillId.덱3)) {
			pc.removeSkillEffect(L1SkillId.덱3);
			pc.getAbility().addAddedDex((byte) -3);
			l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
			int notiWire_spellId = 0;
			int notiWire_duration = 0;
			int notiWire_offIconId = 0;
			boolean notiWire_isGood = false;
			int notiWire_bit = 0;


			{ notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
			{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.덱3; }
			{ notiWire_bit |= 4; notiWire_duration = 0; }
			{ notiWire_bit |= 32; notiWire_offIconId = 976; }
			{ notiWire_bit |= 1024; notiWire_isGood = true; }
			SC_SPELL_BUFF_NOTI.sendFields(
			        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0, 0,
			        notiWire_isGood, 0, 0, 0, notiWire_bit);
			SC_OwnCharAttrDef.send(pc);
		}
		if(pc.hasSkillEffect(L1SkillId.콘3)) {
			pc.removeSkillEffect(L1SkillId.콘3);
			pc.getAbility().addAddedCon((byte) -3);
			l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
			int notiWire_spellId = 0;
			int notiWire_duration = 0;
			int notiWire_offIconId = 0;
			boolean notiWire_isGood = false;
			int notiWire_bit = 0;


			{ notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
			{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.콘3; }
			{ notiWire_bit |= 4; notiWire_duration = 0; }
			{ notiWire_bit |= 32; notiWire_offIconId = 976; }
			{ notiWire_bit |= 1024; notiWire_isGood = true; }
			SC_SPELL_BUFF_NOTI.sendFields(
			        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0, 0,
			        notiWire_isGood, 0, 0, 0, notiWire_bit);
			SC_OwnCharAttrDef.send(pc);
		}
		if(pc.hasSkillEffect(L1SkillId.위즈3)) {
			pc.removeSkillEffect(L1SkillId.위즈3);
			pc.getAbility().addAddedWis((byte) -3);
			l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
			int notiWire_spellId = 0;
			int notiWire_duration = 0;
			int notiWire_offIconId = 0;
			boolean notiWire_isGood = false;
			int notiWire_bit = 0;


			{ notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
			{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.위즈3; }
			{ notiWire_bit |= 4; notiWire_duration = 0; }
			{ notiWire_bit |= 32; notiWire_offIconId = 976; }
			{ notiWire_bit |= 1024; notiWire_isGood = true; }
			SC_SPELL_BUFF_NOTI.sendFields(
			        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0, 0,
			        notiWire_isGood, 0, 0, 0, notiWire_bit);
			SC_OwnCharAttrDef.send(pc);
		}
		if(pc.hasSkillEffect(L1SkillId.인트3)) {
			pc.removeSkillEffect(L1SkillId.인트3);
			pc.getAbility().addAddedInt((byte) -3);
			l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
			int notiWire_spellId = 0;
			int notiWire_duration = 0;
			int notiWire_offIconId = 0;
			boolean notiWire_isGood = false;
			int notiWire_bit = 0;


			{ notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
			{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.인트3; }
			{ notiWire_bit |= 4; notiWire_duration = 0; }
			{ notiWire_bit |= 32; notiWire_offIconId = 976; }
			{ notiWire_bit |= 1024; notiWire_isGood = true; }
			SC_SPELL_BUFF_NOTI.sendFields(
			        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0, 0,
			        notiWire_isGood, 0, 0, 0, notiWire_bit);
			SC_OwnCharAttrDef.send(pc);
		}
		if(pc.hasSkillEffect(L1SkillId.pvp대미지7)) {
			pc.removeSkillEffect(L1SkillId.pvp대미지7);
			pc.getResistance().addPVPweaponTotalDamage(-7);
			l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
			int notiWire_spellId = 0;
			int notiWire_duration = 0;
			int notiWire_offIconId = 0;
			boolean notiWire_isGood = false;
			int notiWire_bit = 0;


			{ notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
			{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.pvp대미지7; }
			{ notiWire_bit |= 4; notiWire_duration = 0; }
			{ notiWire_bit |= 32; notiWire_offIconId = 976; }
			{ notiWire_bit |= 1024; notiWire_isGood = true; }
			SC_SPELL_BUFF_NOTI.sendFields(
			        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0, 0,
			        notiWire_isGood, 0, 0, 0, notiWire_bit);
			SC_OwnCharAttrDef.send(pc);
		}
		if(pc.hasSkillEffect(L1SkillId.pvp감소7)) {
			pc.removeSkillEffect(L1SkillId.pvp감소7);
			pc.addPVPMagicDamageReduction(-7);
			l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
			int notiWire_spellId = 0;
			int notiWire_duration = 0;
			int notiWire_offIconId = 0;
			boolean notiWire_isGood = false;
			int notiWire_bit = 0;


			{ notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
			{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.pvp감소7; }
			{ notiWire_bit |= 4; notiWire_duration = 0; }
			{ notiWire_bit |= 32; notiWire_offIconId = 976; }
			{ notiWire_bit |= 1024; notiWire_isGood = true; }
			SC_SPELL_BUFF_NOTI.sendFields(
			        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0, 0,
			        notiWire_isGood, 0, 0, 0, notiWire_bit);
			SC_OwnCharAttrDef.send(pc);
		}
		if(pc.hasSkillEffect(L1SkillId.근거리대미지5)) {
			pc.removeSkillEffect(L1SkillId.근거리대미지5);
			pc.addDmgup(-5);
			l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
			int notiWire_spellId = 0;
			int notiWire_duration = 0;
			int notiWire_offIconId = 0;
			boolean notiWire_isGood = false;
			int notiWire_bit = 0;


			{ notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
			{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.근거리대미지5; }
			{ notiWire_bit |= 4; notiWire_duration = 0; }
			{ notiWire_bit |= 32; notiWire_offIconId = 976; }
			{ notiWire_bit |= 1024; notiWire_isGood = true; }
			SC_SPELL_BUFF_NOTI.sendFields(
			        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0, 0,
			        notiWire_isGood, 0, 0, 0, notiWire_bit);
			SC_OwnCharAttrDef.send(pc);
		}
		if(pc.hasSkillEffect(L1SkillId.원거리대미지5)) {
			pc.removeSkillEffect(L1SkillId.원거리대미지5);
			pc.addBowDmgup(-5);
			l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
			int notiWire_spellId = 0;
			int notiWire_duration = 0;
			int notiWire_offIconId = 0;
			boolean notiWire_isGood = false;
			int notiWire_bit = 0;


			{ notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
			{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.원거리대미지5; }
			{ notiWire_bit |= 4; notiWire_duration = 0; }
			{ notiWire_bit |= 32; notiWire_offIconId = 976; }
			{ notiWire_bit |= 1024; notiWire_isGood = true; }
			SC_SPELL_BUFF_NOTI.sendFields(
			        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0, 0,
			        notiWire_isGood, 0, 0, 0, notiWire_bit);
			SC_OwnCharAttrDef.send(pc);
		}
		if(pc.hasSkillEffect(L1SkillId.근거리명중5)) {
			pc.removeSkillEffect(L1SkillId.근거리명중5);
			pc.addHitup(-5);
			l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
			int notiWire_spellId = 0;
			int notiWire_duration = 0;
			int notiWire_offIconId = 0;
			boolean notiWire_isGood = false;
			int notiWire_bit = 0;


			{ notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
			{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.근거리명중5; }
			{ notiWire_bit |= 4; notiWire_duration = 0; }
			{ notiWire_bit |= 32; notiWire_offIconId = 976; }
			{ notiWire_bit |= 1024; notiWire_isGood = true; }
			SC_SPELL_BUFF_NOTI.sendFields(
			        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0, 0,
			        notiWire_isGood, 0, 0, 0, notiWire_bit);
			SC_OwnCharAttrDef.send(pc);
		}
		if(pc.hasSkillEffect(L1SkillId.원거리명중5)) {
			pc.removeSkillEffect(L1SkillId.원거리명중5);
			pc.addBowHitup(-5);
			l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
			int notiWire_spellId = 0;
			int notiWire_duration = 0;
			int notiWire_offIconId = 0;
			boolean notiWire_isGood = false;
			int notiWire_bit = 0;


			{ notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
			{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.원거리명중5; }
			{ notiWire_bit |= 4; notiWire_duration = 0; }
			{ notiWire_bit |= 32; notiWire_offIconId = 976; }
			{ notiWire_bit |= 1024; notiWire_isGood = true; }
			SC_SPELL_BUFF_NOTI.sendFields(
			        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0, 0,
			        notiWire_isGood, 0, 0, 0, notiWire_bit);
			SC_OwnCharAttrDef.send(pc);
		}
		if(pc.hasSkillEffect(L1SkillId.sp5)) {
			pc.removeSkillEffect(L1SkillId.sp5);
			pc.getAbility().addSp(-5);
			l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
			int notiWire_spellId = 0;
			int notiWire_duration = 0;
			int notiWire_offIconId = 0;
			boolean notiWire_isGood = false;
			int notiWire_bit = 0;


			{ notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
			{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.sp5; }
			{ notiWire_bit |= 4; notiWire_duration = 0; }
			{ notiWire_bit |= 32; notiWire_offIconId = 976; }
			{ notiWire_bit |= 1024; notiWire_isGood = true; }
			SC_SPELL_BUFF_NOTI.sendFields(
			        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0, 0,
			        notiWire_isGood, 0, 0, 0, notiWire_bit);
			SC_OwnCharAttrDef.send(pc);
		}
		if(pc.hasSkillEffect(L1SkillId.마법적중5)) {
			pc.removeSkillEffect(L1SkillId.마법적중5);
			pc.addMagicHit(-5);
			l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
			int notiWire_spellId = 0;
			int notiWire_duration = 0;
			int notiWire_offIconId = 0;
			boolean notiWire_isGood = false;
			int notiWire_bit = 0;


			{ notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
			{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.마법적중5; }
			{ notiWire_bit |= 4; notiWire_duration = 0; }
			{ notiWire_bit |= 32; notiWire_offIconId = 976; }
			{ notiWire_bit |= 1024; notiWire_isGood = true; }
			SC_SPELL_BUFF_NOTI.sendFields(
			        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0, 0,
			        notiWire_isGood, 0, 0, 0, notiWire_bit);
			SC_OwnCharAttrDef.send(pc);
		}
		if(pc.hasSkillEffect(L1SkillId.기술내성8)) {
			pc.removeSkillEffect(L1SkillId.기술내성8);
			pc.getResistance().addSpecialResistance(eKind.ABILITY, -8);
			l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
			int notiWire_spellId = 0;
			int notiWire_duration = 0;
			int notiWire_offIconId = 0;
			boolean notiWire_isGood = false;
			int notiWire_bit = 0;


			{ notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
			{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.기술내성8; }
			{ notiWire_bit |= 4; notiWire_duration = 0; }
			{ notiWire_bit |= 32; notiWire_offIconId = 976; }
			{ notiWire_bit |= 1024; notiWire_isGood = true; }
			SC_SPELL_BUFF_NOTI.sendFields(
			        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0, 0,
			        notiWire_isGood, 0, 0, 0, notiWire_bit);
			SC_OwnCharAttrDef.send(pc);
		}
		if(pc.hasSkillEffect(L1SkillId.정령내성8)) {
			pc.removeSkillEffect(L1SkillId.정령내성8);
			pc.getResistance().addSpecialResistance(eKind.SPIRIT, -8);
			l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
			int notiWire_spellId = 0;
			int notiWire_duration = 0;
			int notiWire_offIconId = 0;
			boolean notiWire_isGood = false;
			int notiWire_bit = 0;


			{ notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
			{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.정령내성8; }
			{ notiWire_bit |= 4; notiWire_duration = 0; }
			{ notiWire_bit |= 32; notiWire_offIconId = 976; }
			{ notiWire_bit |= 1024; notiWire_isGood = true; }
			SC_SPELL_BUFF_NOTI.sendFields(
			        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0, 0,
			        notiWire_isGood, 0, 0, 0, notiWire_bit);
			SC_OwnCharAttrDef.send(pc);
		}
		if(pc.hasSkillEffect(L1SkillId.용언내성3)) {
			pc.removeSkillEffect(L1SkillId.용언내성3);
			pc.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -3);
			l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
			int notiWire_spellId = 0;
			int notiWire_duration = 0;
			int notiWire_offIconId = 0;
			boolean notiWire_isGood = false;
			int notiWire_bit = 0;


			{ notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
			{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.용언내성3; }
			{ notiWire_bit |= 4; notiWire_duration = 0; }
			{ notiWire_bit |= 32; notiWire_offIconId = 976; }
			{ notiWire_bit |= 1024; notiWire_isGood = true; }
			SC_SPELL_BUFF_NOTI.sendFields(
			        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0, 0,
			        notiWire_isGood, 0, 0, 0, notiWire_bit);
			SC_OwnCharAttrDef.send(pc);
		}
		if(pc.hasSkillEffect(L1SkillId.공포내성3)) {
			pc.removeSkillEffect(L1SkillId.공포내성3);
			pc.getResistance().addSpecialResistance(eKind.FEAR, -3);
			l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
			int notiWire_spellId = 0;
			int notiWire_duration = 0;
			int notiWire_offIconId = 0;
			boolean notiWire_isGood = false;
			int notiWire_bit = 0;


			{ notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
			{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.공포내성3; }
			{ notiWire_bit |= 4; notiWire_duration = 0; }
			{ notiWire_bit |= 32; notiWire_offIconId = 976; }
			{ notiWire_bit |= 1024; notiWire_isGood = true; }
			SC_SPELL_BUFF_NOTI.sendFields(
			        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0, 0,
			        notiWire_isGood, 0, 0, 0, notiWire_bit);
			SC_OwnCharAttrDef.send(pc);
		}
		if(pc.hasSkillEffect(L1SkillId.모든내성3)) {
			pc.removeSkillEffect(L1SkillId.모든내성3);
			pc.getResistance().addSpecialResistance(eKind.ALL, -3);
			l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
			int notiWire_spellId = 0;
			int notiWire_duration = 0;
			int notiWire_offIconId = 0;
			boolean notiWire_isGood = false;
			int notiWire_bit = 0;


			{ notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
			{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.모든내성3; }
			{ notiWire_bit |= 4; notiWire_duration = 0; }
			{ notiWire_bit |= 32; notiWire_offIconId = 976; }
			{ notiWire_bit |= 1024; notiWire_isGood = true; }
			SC_SPELL_BUFF_NOTI.sendFields(
			        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0, 0,
			        notiWire_isGood, 0, 0, 0, notiWire_bit);
			SC_OwnCharAttrDef.send(pc);
		}
		if(pc.hasSkillEffect(L1SkillId.정령적중5)) {
			pc.removeSkillEffect(L1SkillId.정령적중5);
			pc.getResistance().addSpecialPierce(eKind.SPIRIT, -5);
			l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
			int notiWire_spellId = 0;
			int notiWire_duration = 0;
			int notiWire_offIconId = 0;
			boolean notiWire_isGood = false;
			int notiWire_bit = 0;


			{ notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
			{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.정령적중5; }
			{ notiWire_bit |= 4; notiWire_duration = 0; }
			{ notiWire_bit |= 32; notiWire_offIconId = 976; }
			{ notiWire_bit |= 1024; notiWire_isGood = true; }
			SC_SPELL_BUFF_NOTI.sendFields(
			        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0, 0,
			        notiWire_isGood, 0, 0, 0, notiWire_bit);
			SC_OwnCharAttrDef.send(pc);
		}
		if(pc.hasSkillEffect(L1SkillId.용언적중5)) {
			pc.removeSkillEffect(L1SkillId.용언적중5);
			pc.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -5);
			l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
			int notiWire_spellId = 0;
			int notiWire_duration = 0;
			int notiWire_offIconId = 0;
			boolean notiWire_isGood = false;
			int notiWire_bit = 0;


			{ notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
			{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.용언적중5; }
			{ notiWire_bit |= 4; notiWire_duration = 0; }
			{ notiWire_bit |= 32; notiWire_offIconId = 976; }
			{ notiWire_bit |= 1024; notiWire_isGood = true; }
			SC_SPELL_BUFF_NOTI.sendFields(
			        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0, 0,
			        notiWire_isGood, 0, 0, 0, notiWire_bit);
			SC_OwnCharAttrDef.send(pc);
		}
		if(pc.hasSkillEffect(L1SkillId.공포적중5)) {
			pc.removeSkillEffect(L1SkillId.공포적중5);
			pc.getResistance().addSpecialPierce(eKind.FEAR, -5);
			l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
			int notiWire_spellId = 0;
			int notiWire_duration = 0;
			int notiWire_offIconId = 0;
			boolean notiWire_isGood = false;
			int notiWire_bit = 0;


			{ notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
			{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.공포적중5; }
			{ notiWire_bit |= 4; notiWire_duration = 0; }
			{ notiWire_bit |= 32; notiWire_offIconId = 976; }
			{ notiWire_bit |= 1024; notiWire_isGood = true; }
			SC_SPELL_BUFF_NOTI.sendFields(
			        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0, 0,
			        notiWire_isGood, 0, 0, 0, notiWire_bit);
			SC_OwnCharAttrDef.send(pc);
		}
		if(pc.hasSkillEffect(L1SkillId.기술적중5)) {
			pc.removeSkillEffect(L1SkillId.기술적중5);
			pc.getResistance().addSpecialPierce(eKind.ABILITY, -5);
			l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
			int notiWire_spellId = 0;
			int notiWire_duration = 0;
			int notiWire_offIconId = 0;
			boolean notiWire_isGood = false;
			int notiWire_bit = 0;


			{ notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
			{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.기술적중5; }
			{ notiWire_bit |= 4; notiWire_duration = 0; }
			{ notiWire_bit |= 32; notiWire_offIconId = 976; }
			{ notiWire_bit |= 1024; notiWire_isGood = true; }
			SC_SPELL_BUFF_NOTI.sendFields(
			        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0, 0,
			        notiWire_isGood, 0, 0, 0, notiWire_bit);
			SC_OwnCharAttrDef.send(pc);
		}
		if(pc.hasSkillEffect(L1SkillId.모든적중3)) {
			pc.removeSkillEffect(L1SkillId.모든적중3);
			pc.getResistance().addSpecialPierce(eKind.ALL, -3);
			l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
			int notiWire_spellId = 0;
			int notiWire_duration = 0;
			int notiWire_offIconId = 0;
			boolean notiWire_isGood = false;
			int notiWire_bit = 0;


			{ notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
			{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.모든적중3; }
			{ notiWire_bit |= 4; notiWire_duration = 0; }
			{ notiWire_bit |= 32; notiWire_offIconId = 976; }
			{ notiWire_bit |= 1024; notiWire_isGood = true; }
			SC_SPELL_BUFF_NOTI.sendFields(
			        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0, 0,
			        notiWire_isGood, 0, 0, 0, notiWire_bit);
			SC_OwnCharAttrDef.send(pc);
		}
		if(pc.hasSkillEffect(L1SkillId.경험치보너스20)) {
			pc.removeSkillEffect(L1SkillId.경험치보너스20);
			l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
			int notiWire_spellId = 0;
			int notiWire_duration = 0;
			int notiWire_offIconId = 0;
			boolean notiWire_isGood = false;
			int notiWire_bit = 0;


			{ notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
			{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.경험치보너스20; }
			{ notiWire_bit |= 4; notiWire_duration = 0; }
			{ notiWire_bit |= 32; notiWire_offIconId = 976; }
			{ notiWire_bit |= 1024; notiWire_isGood = true; }
			SC_SPELL_BUFF_NOTI.sendFields(
			        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0, 0,
			        notiWire_isGood, 0, 0, 0, notiWire_bit);
			SC_OwnCharAttrDef.send(pc);
		}
		SC_OwnCharAttrDef.send(pc);
	}



}
