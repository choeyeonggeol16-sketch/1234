package l1j.server.AutoHuntSystem;

import l1j.server.Templates.L1EtcItem;
import l1j.server.server.GameClient;
import l1j.server.server.model.L1ItemDelay;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.L1TownLocation;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.ItemId;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_Liquor;
import l1j.server.server.serverpackets.S_PacketBox;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SkillBrave;
import l1j.server.server.serverpackets.S_SkillHaste;
import l1j.server.server.serverpackets.S_SkillIconGFX;
import l1j.server.server.serverpackets.S_SkillIconWisdomPotion;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.serverpackets.S_Sound;
import l1j.server.server.utils.CommonUtil;
import l1j.server.server.utils.SkillUtil;
import l1j.server.server.utils.MessageUtil;

public class AutoHuntItemUse {
	private L1PcInstance owner = null;
	private GameClient client = null;
	
	public AutoHuntItemUse(L1PcInstance pc) {
		owner = pc;
		client = pc.getNetConnection();
	}

	public void toPolyScroll() {
		if (owner.getAutoPolyID() == 0) {
			return;
		}
		
		if (owner.getCurrentSpriteId() == owner.getAutoPolyID()) {
			return;
		}		
		toUseScroll(ItemId.변신_주문서);
	}

	public void toUseItem() {
		useHealPotion();
		useGreenPotion();
		useDragonPearlPotion();
		if (owner.isKnight()) {
			useBravePotion(ItemId.용기_물약);
		} else if (owner.isElf()) {
			useBravePotion(ItemId.엘븐와퍼);
		} else if (owner.isWizard()) {
			useWisdomPotion();
			useBluePotion();
		} else if (owner.isCrown()) {
			useBravePotion(ItemId.악마_피);
		}
	}

	public void toUseScroll(int itemId) {
		ItemInstance item = owner.getInventory().findItemId(itemId);
		if (item == null) {
			return;
		}
        // 딜레이 체크 
		if (!isUseCheck(item)) {
			return;
		}
		switch (itemId) {
		case ItemId.귀환_주문서:
		case ItemId.기란_마을_부적:
			if (owner.getMap().isEscapable() || owner.isGm()) {
				int[] loc = L1TownLocation.getGetBackLoc(L1TownLocation.TOWNID_GIRAN);
				owner.teleport(loc[0], loc[1], (short) loc[2], owner.getHeading(), 169, true, true);
				owner.getInventory().removeItem(item, 1);
				L1ItemDelay.onItemUse(client, item);
			}
			break;
		case ItemId.순간_이동_주문서:
			L1Location newLocation = owner.getLocation().randomLocation(200, true);
			int newX = newLocation.getX();
			int newY = newLocation.getY();
			short mapId = (short) newLocation.getMapId();

			owner.teleport(newX, newY, (short) mapId, owner.getHeading(),169, true, true);
			owner.getInventory().removeItem(item, 1);
			L1ItemDelay.onItemUse(client, item);
			break;
		case ItemId.변신_주문서:
			L1PolyMorph.doPoly(owner, owner.getAutoPolyID(), 1800, L1PolyMorph.MORPH_BY_ITEMMAGIC);
			owner.getInventory().removeItem(item, 1);
			L1ItemDelay.onItemUse(client, item);
			break;
		}
	}

	private void useHealPotion() {
		if (owner.hasSkillEffect(SkillId.디케이_포션)) { // 디케이포션 상태
			owner.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_4));
			return;
		}

		int itemId = owner.getAutoPotion();
		ItemInstance item = owner.getInventory().findItemId(itemId);
		if (item == null) { 
			return;
		}
		// 딜레이 체크 
		if (!isUseCheck(item)) {
			return;
		}
		// 캐릭터의 현재 피가 만피라면 물약을 사용하지 않는다 
		if (owner.getCurrentHp() == owner.getMaxHp()) {
			return;
		}

		if (owner.getInventory().countItems(itemId) < 50) {
			if (owner.isAlarmPotion()) {
				if (owner.getPotionArlamTime() == 0) {
					owner.setPotionArlamTime(System.currentTimeMillis());
				} else {
					if (System.currentTimeMillis() >= owner.getPotionArlamTime() + 2000) {
						owner.setPotionArlamTime(0);
						int sound_num = owner.get_sex() == 0 ? 21238 : 21237;
						owner.sendPackets(new S_Sound(sound_num));
					}
				}
			}
		}
		switch (itemId) {
		
		case ItemId.상아탑_체력_회복제:
		case ItemId.체력_회복제: UseHeallingPotion(owner, CommonUtil.randomRange(16, 50), 189); break;
		case ItemId.농축_체력_회복제: UseHeallingPotion(owner, CommonUtil.randomRange(18, 54), 189); break;
		case ItemId.신속_체력_회복제: UseHeallingPotion(owner, CommonUtil.randomRange(14, 47), 189); break;
		case ItemId.고급_체력_회복제: UseHeallingPotion(owner, CommonUtil.randomRange(46, 74), 194); break;
		case ItemId.농축_고급_체력_회복제: UseHeallingPotion(owner, CommonUtil.randomRange(45, 76), 194); break;
		case ItemId.신속_고급_체력_회복제: UseHeallingPotion(owner, CommonUtil.randomRange(31, 69), 194); break;
		case ItemId.강력_체력_회복제: UseHeallingPotion(owner, CommonUtil.randomRange(31, 69), 197); break;
		case ItemId.농축_강력_체력_회복제: UseHeallingPotion(owner, CommonUtil.randomRange(78, 118), 197); break;
		case ItemId.신속_강력_체력_회복제: UseHeallingPotion(owner, CommonUtil.randomRange(38, 104), 197); break;
		case ItemId.바베큐: UseHeallingPotion(owner, CommonUtil.randomRange(79, 183), 189); break;
		case ItemId.엔트_열매: UseHeallingPotion(owner, CommonUtil.randomRange(60, 80), 197); break;
		case ItemId.바나나_쥬스:
		case ItemId.오렌지_쥬스:
		case ItemId.사과_쥬스: UseHeallingPotion(owner, CommonUtil.randomRange(18, 54), 189); break;
		case ItemId.그을린_빵조각: UseHeallingPotion(owner, CommonUtil.randomRange(18, 58), 189); break;
		case ItemId.타다남은_빵조각: UseHeallingPotion(owner, CommonUtil.randomRange(46, 137), 197); break;
		case ItemId.축복_체력_회복제: UseHeallingPotion(owner, CommonUtil.randomRange(25, 65), 189); break;
		case ItemId.축복_고급_체력_회복제: UseHeallingPotion(owner, CommonUtil.randomRange(55, 89), 194); break;
		case ItemId.축복_강력_체력_회복제: UseHeallingPotion(owner, CommonUtil.randomRange(85, 135), 197); break;
		}
		owner.getInventory().removeItem(item, 1);
		L1ItemDelay.onItemUse(client, item);
	}

	/**
	 * 물약 이팩트 효과를 처리한다.
	 * @param pc
	 * @param healHp
	 * @param gfxid
	 */
	private void UseHeallingPotion(L1PcInstance pc, int healHp, int gfxid) {
		// 아브소르트바리아의 해제
		SkillUtil.cancelBarrier(pc);
		S_SkillSound s = S_SkillSound.get(pc.getId(), gfxid);
		pc.sendPackets(s);
		pc.broadcastPacket(s);
		pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_43)); // \f1기분이 좋아졌습니다.
		
		if (pc.hasSkillEffect(SkillId.플루트_워터)) {
			healHp /= 2;
		}
		pc.setCurrentHp(pc.getCurrentHp() + healHp);
	}


	/**
	 * 자동사냥중인 유저의 1단 가속 상태를 돌려준다.
	 */
	private void useGreenPotion() {
		ItemInstance item = owner.getInventory().findItemId(ItemId.강화_초록_물약);
		if (owner.getMoveSpeed() != 0) {
			return;
		}
		if (item == null) {
			return;
		}
		if (!isUseCheck(item)) {
			return;
		}
		if (owner.hasSkillEffect(SkillId.디케이_포션)) { // 디케이포션 상태
			owner.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_4));
			return;
		}
		int time = 1800;
		S_SkillSound s = S_SkillSound.get(owner.getId(), 191);
		owner.sendPackets(s);
		owner.broadcastPacket(s);
		
		if (owner.getHasteItemEquipped() > 0) {
			return;
		}
		owner.setDrink(false);
		
		if (owner.hasSkillEffect(SkillId.상태_1단_가속)
				|| owner.hasSkillEffect(SkillId.헤이스트)
				|| owner.hasSkillEffect(SkillId.그레이터_헤이스트) 
				|| owner.hasSkillEffect(SkillId.슬로우)
				|| owner.hasSkillEffect(SkillId.매스_슬로우)
				|| owner.hasSkillEffect(SkillId.인탱글)) {
			owner.killSkillEffectTimer(SkillId.상태_1단_가속);
			owner.killSkillEffectTimer(SkillId.그레이터_헤이스트);
			owner.killSkillEffectTimer(SkillId.슬로우);
			owner.killSkillEffectTimer(SkillId.매스_슬로우);
			owner.killSkillEffectTimer(SkillId.인탱글);
			S_SkillHaste Haste = S_SkillHaste.get(owner.getId(), 0, 0);
			owner.sendPackets(Haste);
			owner.broadcastPacket(Haste);	
			owner.setMoveSpeed(0);
		}
		S_SkillHaste skillHaste = S_SkillHaste.get(owner.getId(), 1, time);
		owner.sendPackets(skillHaste);
		owner.broadcastPacket(skillHaste);	
		owner.setMoveSpeed(1);
		owner.setSkillEffect(SkillId.상태_1단_가속, time * 1000);
		owner.getInventory().removeItem(item, 1);
		L1ItemDelay.onItemUse(client, item);
	}

	/**
	 * 자동산냥중인 유저에게 2단 가속 상태를 돌려준다 
	 * @param item_id
	 */
	private void useBravePotion(int item_id) {
		if (owner.getBraveSpeed() != 0) {
			return;
		}	
		ItemInstance item = owner.getInventory().findItemId(item_id);
		if (item == null) {
			// 용기의 물약이 없다
			return;
		}
		if (!isUseCheck(item)) {
			return;
		}		
		if (owner.hasSkillEffect(SkillId.디케이_포션)) { // 디케이포션 상태
			owner.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_4));
			return;
		}
		// 앱솔루트 해제 
		SkillUtil.cancelBarrier(owner);
		
		int time = 0;
		switch (item_id) {
		case ItemId.용기_물약:    time =  300;  break;
		case ItemId.축복_용기_물약: time =  350;  break;
		case ItemId.농축_용기_물약: time =  1800; break;
		case ItemId.엘븐와퍼: 	 time =  600;  break;
		case ItemId.축복_엘븐와퍼:  time =  1200;  break;
		case ItemId.농축_집중_물약: time = 1800;  break;
		case ItemId.악마_피:     time =  350;  break;	
		case ItemId.농축_악마_피: time =  1800;  break;
		}
		if (owner.hasSkillEffect(SkillId.윈드_워크)
				|| owner.hasSkillEffect(SkillId.윈드_워크)
				|| owner.hasSkillEffect(SkillId.무빙_악셀레이션) 
				|| owner.hasSkillEffect(SkillId.상태_요정_2단_가속)
				|| owner.hasSkillEffect(SkillId.상태_2단_가속)) {
			owner.killSkillEffectTimer(SkillId.홀리_워크);
			owner.killSkillEffectTimer(SkillId.윈드_워크);
			owner.killSkillEffectTimer(SkillId.무빙_악셀레이션);
			owner.killSkillEffectTimer(SkillId.상태_요정_2단_가속);
			owner.killSkillEffectTimer(SkillId.상태_2단_가속);
			S_SkillBrave ss = S_SkillBrave.get(owner.getId(), 0, 0);
			owner.sendPackets(ss);
			owner.broadcastPacket(ss);	
			owner.setBraveSpeed(0);
		}
		if(owner.isKnight()) {
			if(item_id == ItemId.용기_물약 || item_id == ItemId.축복_용기_물약 || item_id == ItemId.농축_용기_물약) {
				S_SkillBrave ss = S_SkillBrave.get(owner.getId(),  1, time);
				owner.sendPackets(ss);
				owner.broadcastPacket(ss);	
				owner.setSkillEffect(SkillId.상태_2단_가속, time * 1000);
				owner.setBraveSpeed(1);
			}
		}
		else if(owner.isElf()) {
			if(item_id == ItemId.엘븐와퍼 || item_id == ItemId.축복_엘븐와퍼 || item_id == ItemId.농축_집중_물약) {
				S_SkillBrave ss = S_SkillBrave.get(owner.getId(),  3, time);
				owner.sendPackets(ss);
				owner.broadcastPacket(ss);	
				owner.setSkillEffect(SkillId.상태_요정_2단_가속, time * 1000);	
				owner.setBraveSpeed(1); 
			}
		}
		else if(owner.isCrown()) {
			if(item_id == ItemId.악마_피 || item_id == ItemId.농축_악마_피) {
				S_SkillBrave ss = S_SkillBrave.get(owner.getId(),  1, time);
				owner.sendPackets(ss);
				owner.broadcastPacket(ss);
				owner.setSkillEffect(SkillId.상태_2단_가속, time * 1000);	
				owner.setBraveSpeed(1); 
			}
		}
		S_SkillSound s = S_SkillSound.get(owner.getId(), 751);
		owner.sendPackets(s);
		owner.broadcastPacket(s);
		owner.getInventory().removeItem(item, 1);
		L1ItemDelay.onItemUse(client, item);
	}

	private void useDragonPearlPotion() {
		if (owner.hasSkillEffect(SkillId.상태_드래곤_진주_3단계))
			return;

		ItemInstance item = owner.getInventory().findItemId(49026);
		if (item == null) {
			// 드래곤의 진주가 없다
			return;
		}
		if (!isUseCheck(item))
			return;

		if (owner.hasSkillEffect(SkillId.디케이_포션)) { // 디케이포션 상태
			owner.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_4));
			return;
		}

		if (owner.hasSkillEffect(SkillId.상태_드래곤_진주_3단계)) {
			owner.killSkillEffectTimer(SkillId.상태_드래곤_진주_3단계);
			owner.sendPackets(new S_PacketBox(S_PacketBox.DRAGONPERL, 0, 0));
			owner.broadcastPacket(new S_Liquor(owner.getId(), 0));
			owner.sendPackets(new S_Liquor(owner.getId(), 0));
		}
		// 앱솔루트 해제 
		SkillUtil.cancelBarrier(owner);
		int time = 600 * 1000;
		int stime = ((time / 1000) / 4) - 2;
		owner.setSkillEffect(SkillId.상태_드래곤_진주_3단계, time);
		owner.sendPackets(new S_PacketBox(S_PacketBox.DRAGONPERL, stime, 8));
		owner.sendPackets(new S_Liquor(owner.getId(), 8));
		owner.broadcastPacket(new S_Liquor(owner.getId(), 8));
		S_SkillSound s = S_SkillSound.get(owner.getId(), 197);
		owner.sendPackets(s);
		owner.broadcastPacket(s);
		owner.getInventory().removeItem(item, 1);
		L1ItemDelay.onItemUse(client, item);
		owner.sendPackets(new S_ServerMessage(1065));
	}

	private void useWisdomPotion() {
		if (owner.hasSkillEffect(SkillId.디케이_포션)) { // 디케이포션 상태
			owner.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_4)); 
			return;
		}
		SkillUtil.cancelBarrier(owner);
		
		ItemInstance item = owner.getInventory().findItemId(210113);
		if (item == null) {
			// 지혜의 물약이 없다
			return;
		}
		if (!isUseCheck(item)) {
			return;
		}
			
		if (owner.hasSkillEffect(SkillId.상태_지혜)) {
			owner.killSkillEffectTimer(SkillId.상태_지혜);
		}
		// 앱솔루트 해제 

		int time = 1000;
		owner.sendPackets(new S_SkillIconWisdomPotion((int) (time)));
		S_SkillSound s = S_SkillSound.get(owner.getId(), 750);
		owner.sendPackets(s);
		owner.broadcastPacket(s);
		owner.setSkillEffect(SkillId.상태_지혜, time * 1000);
		owner.getInventory().removeItem(item, 1);
		L1ItemDelay.onItemUse(client, item);
	}

	private void useBluePotion() {
		if (owner.hasSkillEffect(SkillId.디케이_포션)) { // 디케이포션 상태
			owner.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_4)); 
			return;
		}
		ItemInstance item = owner.getInventory().findItemId(210114);
		if (item == null) {
			// 파란물약이 없다
			return;
		}
		if (!isUseCheck(item)) {
			return;
		}
	
		if (owner.hasSkillEffect(SkillId.상태_파란물약)) {
			owner.killSkillEffectTimer(SkillId.상태_파란물약);
			owner.addMpr(-2);
		}
		// 앱솔루트 해제 
		SkillUtil.cancelBarrier(owner);
		int time = 1800;

		owner.addMpr(2);
		owner.sendPackets(new S_SkillIconGFX(34, time));
		S_SkillSound s = S_SkillSound.get(owner.getId(), 190);
		owner.sendPackets(s);
		owner.broadcastPacket(s);
		owner.setSkillEffect(SkillId.상태_파란물약, time * 1000);
		owner.sendPackets(new S_ServerMessage(1007));
		owner.getInventory().removeItem(item, 1);
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
			if (owner.hasItemDelay(delay_id)) {
				return false;
			}
		}
		return true;
	}

}
