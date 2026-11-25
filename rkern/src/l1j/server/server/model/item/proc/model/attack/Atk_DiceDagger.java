package l1j.server.server.model.item.proc.model.attack;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.item.proc.model.ItemSkillModel;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.utils.GameUtils;

public class Atk_DiceDagger extends ItemSkillModel{

	@Override
	public double get(L1Character attacker, L1Character defender, L1ItemInstance t_item, double dwd) {
		if(!isPlay(attacker, t_item))
			return 0D;
		
		int hp = defender.getCurrentHp();
		if(hp <= 0)
			return 0D;
		
		if(isCounterMagic(defender) || GameUtils.isUnbeatable(defender)){
			remove(attacker, t_item);
			return 0D;
		}
		
		double dmg = (hp * 2) / 3;
		dmg = GameUtils.getMagicDamage(attacker,  defender, (int)dmg);
		dmg = calcEnchant(t_item, dmg);
		dmg = calcAttr(defender, dmg);
		dmg = calcStat(attacker, dmg);
		
		if(eff_id > 0)
			SC_SkillSound.sendAndBroadcast(defender, defender.getId(), eff_id);
		remove(attacker, t_item);
		if(dmg >= defender.getCurrentHp() + 10)
			return defender.getCurrentHp() - 10;
		return dmg;
	}

	private void remove(L1Character c, L1ItemInstance item){
		if (c instanceof L1PcInstance) SC_ServerMessage.send(((L1PcInstance)c), 158, item.getLogName());
		c.getInventory().DeleteItemAmount(item, 1);
	}
}
