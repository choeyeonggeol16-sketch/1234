package l1j.server.server.model.item.proc.model.attack;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.world.effect.L1EffectSpawn;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.item.proc.model.ItemSkillModel;
import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.utils.GameUtils;

public class Atk_Hold extends ItemSkillModel{

	@Override
	public double get(L1Character attacker, L1Character defender, L1ItemInstance t_item, double dwd) {
		if(GameUtils.isUnbeatable(defender) || defender.hasSkillEffect(L1SkillId.STATUS_FREEZE) || !isPlay(attacker, t_item))
			return 0D;
		
		int pure = getProbability(attacker, t_item);
		if(is_magic){
			if(!GameUtils.isMagicSuccess(attacker, defender, pure))
				return 0D;
			
			if(isCounterMagic(defender))
				return 0D;
		}else{
			if(!isInPercent(pure))
				return 0D;
		}
		
		double time =  _rnd.nextInt(max_val - min_val) + min_val;
		time 		= calcEnchant(t_item, time);
		time		= calcAttr(defender, time);
		time		= calcStat(attacker, time);
		int t		= (int)time;
		L1EffectSpawn.getInstance().spawnEffect(81182, t, defender.getX(), defender.getY(), defender.getMapId());
		defender.setSkillEffect(L1SkillId.STATUS_FREEZE, t);
		
		if(eff_id > 0)
			SC_SkillSound.sendAndBroadcast(defender, defender.getId(), eff_id);
		SC_Paralysis.send(defender, SC_Paralysis.TYPE_BIND, true);
		return 0D;
	}

}
