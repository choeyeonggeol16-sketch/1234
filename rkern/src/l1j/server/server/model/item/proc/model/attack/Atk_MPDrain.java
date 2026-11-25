package l1j.server.server.model.item.proc.model.attack;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.item.proc.model.ItemSkillModel;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.utils.GameUtils;

public class Atk_MPDrain extends ItemSkillModel{

	@Override
	public double get(L1Character attacker, L1Character defender, L1ItemInstance t_item, double dwd) {
		double dmg = _rnd.nextInt(max_val - min_val) + min_val;
		int enchant = t_item.getEnchantLevel();
		if(enchant >= 7){
			dmg += ((enchant - 7) + 4);
		}
		
		if(dmg > e_val)
			dmg = e_val;
		
		if(eff_id > 0)
			SC_SkillSound.sendAndBroadcast(defender, defender.getId(), eff_id);
		
		if(GameUtils.isUnbeatable(defender))
			return 0D;
		if(t_item.getItemId() == 602){
			/*
			if(attacker instanceof L1PcInstance){
				L1PcInstance pc = (L1PcInstance)attacker;
				pc.sendPackets("상대 마법 리덕션 : "+defender.getPVPMagicDamageReduction());
				pc.sendPackets("상대 트루 mr : "+defender.getTrueMr());
				pc.sendPackets("상대 저항 mr : "+defender.getResistance().getEffectedMrBySkill());
				pc.sendPackets("다른 mr: " + defender.getResistance().getMr());
				
				pc.sendPackets("상대 이름 : "+defender.getName());
			}
			*/
			//마력의 단검 마방 60이상일 때 무의미
			if(defender instanceof L1PcInstance){
			if(defender.GetMDefendBySkill() >= 60){
				return 0D;
			}
			}

		}
		if(defender.getCurrentMp() < dmg)
			dmg = defender.getCurrentMp();
		//
		/**헤이트 취합 확인하기 Neige**/
		/*
		if(defender instanceof L1NpcInstance){
			if(((L1NpcInstance) defender).getHateList().containsKey(attacker)){
				//if(((L1NpcInstance) defender).getHateList().getTotalHate() >= 100){
					if(((L1PcInstance) attacker).isGm()){
						((L1PcInstance) attacker).sendPackets("헤이트 total : "+((L1NpcInstance) defender).getHateList().getTotalHate());
						((L1PcInstance) attacker).sendPackets("헤이트 cha : "+((L1NpcInstance) defender).getHateList().get(attacker));
					}
				//}
			}
		}
		*/
		//
		defender.setCurrentMp((int)(defender.getCurrentMp() - dmg));
		attacker.setCurrentMp((int)(attacker.getCurrentMp() + dmg));
		return 0D;
	}
}