package l1j.server.AutoPotionSystem;

import static l1j.server.server.model.skill.SkillId.디케이_포션;
import l1j.server.commons;
import l1j.server.Templates.L1EtcItem;
import l1j.server.server.GameClient;
import l1j.server.server.model.L1ItemDelay;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.HealingPotion;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_Sound;
import l1j.server.server.utils.SkillUtil;
import l1j.server.server.utils.MessageUtil;

public class AutoPotionUse {
	private L1PcInstance owner = null;
	private GameClient client = null;

	public AutoPotionUse(L1PcInstance pc) {
		owner = pc;
		client = pc.getNetConnection();
	}

	public void toUseItem() {
		useHealPotion();
	}

	private void useHealPotion() { 
		
		if (owner.hasSkillEffect(디케이_포션)) {
			owner.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_4));
			return;
		}
		if (commons.isNonAction(owner)) {
			return;
		}
		// 앱솔루트 해제 
		SkillUtil.cancelBarrier(owner);
		
		int itemId = owner.getAutoPotion();
		ItemInstance item = owner.getInventory().findItemId(itemId);
		
		if (item == null) {
			owner.EndAutoPotionController();
			owner.setAutoPotionUse(false);
			owner.sendPackets("[물약소진]자동 물약을 종료합니다.");
			return;
		}
		
		if (owner.isDead()) {
			owner.EndAutoPotionController();
			owner.setAutoPotionUse(false);
			owner.sendPackets("[다이]자동 물약을 종료합니다.");
			return;
		}
		
		if (owner.isInvisble())
			return;
		
		if (!isUseCheck(item)) { 
			return;
		}
		
		int percent = (int) Math.round(((double) owner.getCurrentHp() / (double) owner.getMaxHp()) * 100);
		if (percent > owner.getAutoPotion_Percent()) {
			return;
		}
		
		if (owner.getInventory().countItems(itemId) < 50 && owner.getInventory().countItems(itemId) > 0) {
			if (owner.isAlarmPotion()) {
				if (owner.getPotionArlamTime() == 0) {
					owner.setPotionArlamTime(System.currentTimeMillis());
				} else {
					if (System.currentTimeMillis() >= owner.getPotionArlamTime() + 2000) {
						owner.setPotionArlamTime(0);
				//		int sound_num = owner.get_sex() == 0 ? 21238 : 21237;
						owner.sendPackets(new S_Sound(21237));
					}
				}
			}
		} 
		HealingPotion healingPotion = HealingPotion.get(itemId);
		if (healingPotion != null) {  
			healingPotion.use(owner, item); 
		}
 
		L1ItemDelay.onItemUse(client, item);
	}

	private boolean isUseCheck(ItemInstance item) {
		int delay_id = 0;
		if (item.getItem().getType2() == 0) {
			if (item.getItem() instanceof L1EtcItem) {
				delay_id = ((L1EtcItem) item.getItem()).get_delayid();
			}
		}
		if (delay_id != 0) {
			if (owner.hasItemDelay(delay_id) == true) {
				return false;
			}
		}
		return true;
	}
}


