package l1j.server.MJItemSkillSystem.Model.Attack;

import l1j.server.MJItemSkillSystem.Model.MJItemSkillModel;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.MJPassiveSkill.MJPassiveID;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;

import l1j.server.server.utils.MJCommons;

public class Atk_ChainSpot extends MJItemSkillModel{
	
	@Override
	public double get(L1Character attacker, L1Character defender, L1ItemInstance t_item, double dwd) {
		if(MJCommons.isUnbeatable(defender) || !isPlay(attacker, t_item))
			return 0D;
		
		int pure = getProbability(attacker, t_item);
		if(is_magic){
			if(!MJCommons.isMagicSuccess(attacker, defender, pure))
				return 0D;
			
			if(isCounterMagic(defender))
				return 0D;
		}else{
			if(!isInPercent(pure))
				return 0D;
		}
		
		if(attacker.hasSkillEffect(L1SkillId.CHAINSWORD1)){
			attacker.killSkillEffectTimer(L1SkillId.CHAINSWORD1);
			attacker.setSkillEffect(L1SkillId.CHAINSWORD2, 15000);
			if (attacker instanceof L1PcInstance) SC_PacketBox.send(((L1PcInstance)attacker), SC_PacketBox.SPOT, 2);
		}else if(attacker.hasSkillEffect(L1SkillId.CHAINSWORD2)){
			attacker.killSkillEffectTimer(L1SkillId.CHAINSWORD2);
			attacker.setSkillEffect(L1SkillId.CHAINSWORD3, 15000);
			if (attacker instanceof L1PcInstance) SC_PacketBox.send(((L1PcInstance)attacker), SC_PacketBox.SPOT, 3);	
		}else if(attacker.hasSkillEffect(L1SkillId.CHAINSWORD3) && attacker instanceof L1PcInstance){
			L1PcInstance pc = (L1PcInstance)attacker;
			if(pc.isPassive(MJPassiveID.FOU_SLAYER_BRAVE.toInt())){
				attacker.setSkillEffect(L1SkillId.CHAINSWORD4, 15000);
				if (attacker instanceof L1PcInstance) SC_PacketBox.send(((L1PcInstance)attacker), SC_PacketBox.SPOT, 4);
			}
		}else if(attacker.hasSkillEffect(L1SkillId.CHAINSWORD4)){
		}else {
			attacker.setSkillEffect(L1SkillId.CHAINSWORD1, 15000);
			if (attacker instanceof L1PcInstance) SC_PacketBox.send(((L1PcInstance)attacker), SC_PacketBox.SPOT, 1);			
		}
		
		if(eff_id > 0){
			SC_SkillSound.sendAndBroadcast(defender, defender.getId(), eff_id);
		}
		return 0D;
	}
}
