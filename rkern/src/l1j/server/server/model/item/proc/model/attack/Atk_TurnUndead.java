package l1j.server.server.model.item.proc.model.attack;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.item.proc.model.ItemSkillModel;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.utils.GameUtils;

public class Atk_TurnUndead extends ItemSkillModel{

	@Override
	public double get(L1Character attacker, L1Character defender, L1ItemInstance t_item, double dwd) {
		if(GameUtils.isUnbeatable(defender) || !isPlay(attacker, t_item) || !(defender instanceof L1MonsterInstance))
			return 0D;
		
		L1MonsterInstance mon = (L1MonsterInstance)defender;
		int und = mon.getNpcTemplate().get_undead();
		if(und != 1 && und != 3)
			return 0D;
		
		double pure = getProbability(attacker, t_item);
		pure = calcEnchant(t_item, pure);
		if(is_magic){
			if(!GameUtils.isMagicSuccess(attacker, defender, (int)pure))
				return 0D;
			
			if(isCounterMagic(defender))
				return 0D;
		}else{
			if(!isInPercent((int)pure))
				return 0D;
		}
		if(attacker.getMapId() == 15403 || attacker.getMapId() == 15430){
			return 0D;
		}
		if(eff_id > 0)
			SC_SkillSound.sendAndBroadcast(defender, defender.getId(), eff_id);
		mon.receiveDamage(attacker, mon.getCurrentHp());
		return 0D;
	}
}
