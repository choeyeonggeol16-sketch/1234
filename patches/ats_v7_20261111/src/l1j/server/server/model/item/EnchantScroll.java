package l1j.server.server.model.item;

import java.text.DecimalFormat;

import l1j.server.Template.LORnd;
import l1j.server.Templates.L1Item;
import l1j.server.configure.Config;
import l1j.server.server.datatables.AccessoryEnchantInformationTable;
import l1j.server.server.datatables.ArmorEnchantInformationTable;
import l1j.server.server.datatables.ItemMessageTable;
import l1j.server.server.datatables.ItemMessageTable.L1ItemMessage;
import l1j.server.server.datatables.ItemSettingTable;
import l1j.server.server.datatables.WeaponEnchantInformationTable;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.random.RandomGenerator;
import l1j.server.server.random.RandomGeneratorFactory;
import l1j.server.server.serverpackets.S_OwnCharStatus;
import l1j.server.server.serverpackets.S_PacketBox;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.utils.CommonUtil;
import l1j.server.server.utils.MessageUtil;
import l1j.server.server.utils.TYUtil;
import manager.SJManager;

public class EnchantScroll {
	private static RandomGenerator _random = RandomGeneratorFactory.newRandom();

	public static EnchantScroll getInstance() {
		return LazyHolder.instance;
	}
	private static class LazyHolder {
		private static final EnchantScroll instance = new EnchantScroll();
	}
	/**
	 * 인챈트 관련 오류가 너무 많아 새로 만듬. SJ
	 * @param pc
	 * @param item
	 * @param target
	 * @return
	 */
	public boolean use(L1PcInstance pc, ItemInstance item, ItemInstance target) {
		boolean result = false;

		switch (item.getItemId()) {
		case ItemId.불_무기_강화_주문서: case ItemId.물_무기_강화_주문서: case ItemId.바람_무기_강화_주문서: case ItemId.지령_무기_강화_주문서: result = enchantAttrWeapon(pc, item, target); break;
		case ItemId.장신구_주문서: case ItemId.오림_장신구_마법_주문서: case ItemId.축복_오림_장신구_마법_주문서: result = enchantAccessory(pc, item, target); break;
		case ItemId.무기_마법_주문서: case ItemId.축복_무기_마법_주문서: case ItemId.저주_무기_마법_주문서: result = enchantWeapon(pc, item, target); break;
		case ItemId.갑옷_마법_주문서: case ItemId.축복_갑옷_마법_주문서: case ItemId.저주_갑옷_마법_주문서: result = enchantArmor(pc, item, target); break;
		case ItemId.용의_티셔츠_강화_주문서: case ItemId.축복_용의_티셔츠_강화_주문서: case ItemId.저주_용의_티셔츠_강화_주문서: result = enchantDradonT(pc, item, target); break;
		case ItemId.갑옷_파괴_주문서:  result = destroyArmor(pc, item, target); break;
		case ItemId.룸티스_강화_주문서:  result = enchantRoomtis(pc, item, target); break;
		case ItemId.스냅퍼_강화_주문서:  result = enchantSnapper(pc, item, target); break;
		case ItemId.장인_무기_마법_주문서:  result = masterWeaponMagicScroll(pc, item, target); break;
		case ItemId.장인_갑옷_마법_주문서:  result = masterArmorMagicScroll(pc, item, target); break;
		case ItemId.고대_서_갑옷: result = ancientArmorScroll(pc, item, target); break;
		case ItemId.고대_서_무기: result = ancientWeaponScroll(pc, item, target); break;
		}	
		return result;
	}
	private boolean ancientArmorScroll(L1PcInstance pc, ItemInstance item, ItemInstance target) {
		int 아이템아이디 = item.getItemId(), 타겟아이템아이디 = target.getItem().getItemId(),
				인챈트레벨 = target.getEnchantLevel(), 안전인챈 = target.getItem().get_safeenchant(),
						rnd = _random.nextInt(10000) + 1,  aa = Config.고대_서_갑옷_인챈트_확률, enchant_chance_armor = 0;
		if (target == null || target.getItem().getType2() != L1Item.CATEGORY_ARMOR 
				|| target.isAccessory()  || target.isIvoryTowerArmor(타겟아이템아이디)
				|| target.isDragonT(타겟아이템아이디)
				|| target.cashItems(타겟아이템아이디)) {			
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
			return false;
		}
		if (안전인챈 < 0) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
			return false;
		}
		if (target.getBless() >= 128) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
			return false;
		}
		if (인챈트레벨 >= Config.고대_서_갑옷_인챈트) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_19), true);
			return false;
		}	
		enchant_chance_armor = (int) (aa * 100.0D);

		if (rnd <= enchant_chance_armor) {
			successEnchant(pc, target, 1);
			pc.getInventory().saveItem(target, L1PcInventory.COL_ENCHANTLVL);
		} else if (아이템아이디 == ItemId.고대_서_갑옷) {
			if (인챈트레벨 >= 0 && _random.nextInt(100) < 100 - 인챈트레벨) {
				successEnchant(pc, target, -1);
				pc.getInventory().saveItem(target, L1PcInventory.COL_ENCHANTLVL);
			}
		} else {
			failureEnchant(pc, target);
		}
		if (pc.isGm()) {
			DecimalFormat format = new DecimalFormat(".##");
			pc.sendPackets(new S_SystemMessage("\\fT인챈트 - 레벨: " + 인챈트레벨 + " / 성공확률: " + format.format(enchant_chance_armor / 100.0D)
					+ " >= 랜덤: " + format.format(rnd / 100.0D)));
		}
		return true;	
	}
    private boolean ancientWeaponScroll(L1PcInstance pc, ItemInstance item, ItemInstance target) {
    	int 아이템아이디 = item.getItemId(), 타겟아이디 = target.getItem().getItemId(), 인챈트레벨 = target.getEnchantLevel(),
				안전인챈 = target.getItem().get_safeenchant(), 랜덤확률 = _random.nextInt(10000) + 1, 
						aa = Config.고대_서_무기_인챈트_확률,
						enchant_chance_weapon = 0;
		
		if (target == null || target.getItem().getType2() != L1Item.CATEGORY_WEAPON || target.isIvoryTowerWeapon(타겟아이디)) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
			return false;
		}
		if(안전인챈 < 0) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
			return false;
		}
		if(target.getBless() >= 128) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
			return false;
		}
		if (인챈트레벨 >= Config.고대_서_무기_인챈트) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_19), true);
			return false;
		}			
		enchant_chance_weapon = (int) (aa * 100.0D);

		if (랜덤확률 <= enchant_chance_weapon) {
			successEnchant(pc, target, 1);
			pc.getInventory().saveItem(target, L1PcInventory.COL_ENCHANTLVL);
		} else if (아이템아이디 == ItemId.고대_서_무기) {
			if (인챈트레벨 >= 0 && _random.nextInt(100) < 100 - 인챈트레벨) {
				successEnchant(pc, target, -1);
				pc.getInventory().saveItem(target, L1PcInventory.COL_ENCHANTLVL);
			}
		} else {
			failureEnchant(pc, target);
		}
		if (pc.isGm()) {
			DecimalFormat format = new DecimalFormat(".##");
			pc.sendPackets(new S_SystemMessage("\\fT인챈트 - 레벨: " + 인챈트레벨 + " / 성공확률: " + format.format(enchant_chance_weapon / 100.0D)
					+ " >= 랜덤: " + format.format(랜덤확률 / 100.0D)));
		}
		return true;
		
	}
	private boolean masterArmorMagicScroll(L1PcInstance pc, ItemInstance item, ItemInstance target) {
		int armorId = target.getItemId(), enchantLevel = target.getEnchantLevel(), 
						bb = ArmorEnchantInformationTable.getInstance().getChance(ItemId.장인_갑옷_마법_주문서 ,enchantLevel);
		if (target == null || target.getItem().getType2() != L1Item.CATEGORY_ARMOR 
				|| target.isAccessory()  || target.isIvoryTowerArmor(armorId) || target.isDragonT(armorId)) {			
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
			return false;
		}
		
		if (enchantLevel >= Config.장인_갑옷_마법_주문서_인챈트_제한) { 
			int[] bossIds = TYUtil.GetIntTokenizeIntArray(Config.MASTER_LISTQ);
			for (int i = 0; i < bossIds.length; i++) {
				if (armorId == bossIds[i]) {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
					return false;
				}
			}   
	        if (enchantLevel >= Config.장인_갑옷_마법_주문서_인챈트_맥스_제한) {
	    		   pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_19), true);
				return false;
			}
	
			if (pc.isGm()) { 
				pc.sendPackets(new S_SystemMessage("장인 갑옷 마법 주문서의 확률은 :" + (bb / 10000.0D) + "%"));
			} 
			
			if (LORnd.isWinning(1000000, bb)) { 
				successEnchant(pc, target, 1);
				pc.getInventory().removeItem(item, 1);
			} else {
				pc.sendPackets(new S_ServerMessage(1310), true);
				pc.getInventory().removeItem(item, 1);
				return false;
				// 인챈트: 강렬하게 빛났지만 아무 일도 없었습니다.
			}  
		} else {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
			return false;
		}
		return true;
	}
	private boolean masterWeaponMagicScroll(L1PcInstance pc, ItemInstance item, ItemInstance target) {
		int weaponId = target.getItemId(), enchantLevel = target.getEnchantLevel(), 
						aa = WeaponEnchantInformationTable.getInstance().getChance(ItemId.장인_무기_마법_주문서 ,enchantLevel);
		
		if (target == null || target.getItem().getType2() != L1Item.CATEGORY_WEAPON || target.isIvoryTowerWeapon(weaponId)) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
			return false;
		}
		if (enchantLevel >= Config.장인_무기_마법_주문서_인챈트_제한) { 

			int[] bossIds = TYUtil.GetIntTokenizeIntArray(Config.MASTER_LISTA);
			for (int i = 0; i < bossIds.length; i++) {
				if (weaponId == bossIds[i]) {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
					return false;
				}
			}   
	       if (enchantLevel >= Config.장인_무기_마법_주문서_인챈트_맥스_제한) {
	   		   pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_19), true);
				return false;
			} 
	       //장인_무기 마법 주문서 불가한 아이템
			if (weaponId == ItemId.바람칼날_단검 || weaponId == ItemId.진명황_집행검
					|| weaponId == ItemId.붉은_그림자_이도류|| weaponId == ItemId.수정결정체_지팡이
					|| weaponId == ItemId.가이아_격노 || weaponId == ItemId.야수왕_크로우) {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
					return false;
			 }
	       
			if (pc.isGm()) { 
				pc.sendPackets(new S_SystemMessage("장인 무기 마법 주문서의 확률은 : " + (aa / 10000.0D) + "%"));
			}
			if (LORnd.isWinning(1000000, aa)) { 
				successEnchant(pc, target, 1);
				pc.getInventory().removeItem(item, 1);
			} else {
				pc.sendPackets(new S_ServerMessage(1310), true);
				pc.getInventory().removeItem(item, 1);
				return false;
			}  
		} else {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
			return false;
		}
		return true;
	}
	private boolean destroyArmor(L1PcInstance pc, ItemInstance item, ItemInstance target) {
		if (target.getItem().getType2() == L1Item.CATEGORY_ARMOR) {
			int msg = 0;
			switch (target.getItem().getType()) {
			case 1: msg = 171; break;
			case 2: msg = 169; break;
			case 3: msg = 170; break;
			case 4: msg = 168; break;
			case 5: msg = 172; break;
			case 6: msg = 173; break;
			case 7: msg = 174; break;
			default:
				msg = 167;
			}
			pc.sendPackets(new S_ServerMessage(msg), true);
			pc.getInventory().removeItem(target, 1);
			pc.getInventory().saveItem(target, L1PcInventory.COL_ENCHANTLVL);
		} else {
			pc.sendPackets(new S_ServerMessage(154), true);
			pc.getInventory().saveItem(target, L1PcInventory.COL_ENCHANTLVL);
		}
		return true;
	}
	private boolean enchantWeapon(L1PcInstance pc, ItemInstance item, ItemInstance target) {
		int bless = item.getBless(),
				weaponId = target.getItem().getItemId(), enchantLevel = target.getEnchantLevel(),
				safeEnchant = target.getItem().get_safeenchant(), rnd = _random.nextInt(10000) + 1, 
						aa = WeaponEnchantInformationTable.getInstance().getChance(-1,enchantLevel),
						bb = WeaponEnchantInformationTable.getInstance().getChance(-2,enchantLevel),
								enchant_chance_weapon = 0;
		
		if (target == null || target.getItem().getType2() != L1Item.CATEGORY_WEAPON || target.isIvoryTowerWeapon(weaponId)) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
			return false;
		}
		if(safeEnchant < 0) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
			return false;
		}
		if(target.getBless() >= 128) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
			return false;
		}
		if (bless % 128 == 2) {
			if (enchantLevel > -safeEnchant) {
				successEnchant(pc, target, -1);
				pc.getInventory().saveItem(target, L1PcInventory.COL_ENCHANTLVL);
			} else {
				failureEnchant(pc, target);
				pc.getInventory().deleteItem(target);
			}
		} else if (enchantLevel < safeEnchant && safeEnchant > 0) {
			successEnchant(pc, target, randomLevel(target, bless));
			pc.getInventory().saveItem(target, L1PcInventory.COL_ENCHANTLVL);
		} else { 
			enchantLevel = Math.abs(enchantLevel);
			int maxSetting = ItemSettingTable.getInstance().getEnchantMax(weaponId);
			if (maxSetting != -1 && enchantLevel >= maxSetting) {
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_19), true);
				return false;
			}
			if (enchantLevel >= Config.무기인첸트) {
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_19), true);
				return false;
			}
			if (safeEnchant == 0) {
				enchant_chance_weapon = (int) (aa + ItemSettingTable.getInstance().getEnchantChance(target) * 100.0D);
			} else {
				enchant_chance_weapon = (int) (bb + ItemSettingTable.getInstance().getEnchantChance(target) * 100.0D);
			}	  
			if (rnd <= enchant_chance_weapon) {		
				successEnchant(pc, target, randomLevel(target, bless));
				pc.getInventory().saveItem(target, L1PcInventory.COL_ENCHANTLVL);
			} else if ((enchantLevel >= 9) && (rnd < enchant_chance_weapon * 2)) {
				pc.sendPackets(new S_ServerMessage(160, target.getLogName(), "$245", "$248"));
			} else {
				failureEnchant(pc, target);
				pc.getInventory().deleteItem(target);
			}
			if (pc.isGm()) {
				DecimalFormat format = new DecimalFormat(".##");
				pc.sendPackets(new S_SystemMessage("\\fT무기 - 인챈트: " + enchantLevel + " / 성공확률: " + format.format(enchant_chance_weapon / 100.0D)
						+ "%  >= 랜덤: " + format.format(rnd / 100.0D)));
			}
		}
		return true;
	}
	private boolean enchantArmor(L1PcInstance pc, ItemInstance item, ItemInstance target) {
		int bless = item.getBless(), safeEnchant = target.getItem().get_safeenchant(), 
				armorId = target.getItem().getItemId(), enchantLevel = target.getEnchantLevel(), rnd = _random.nextInt(10000) + 1,
						aa = ArmorEnchantInformationTable.getInstance().getChance(-1,enchantLevel), 
						bb = ArmorEnchantInformationTable.getInstance().getChance(-2,enchantLevel),
					    cc = ArmorEnchantInformationTable.getInstance().getChance(-3,enchantLevel), 
					    enchant_chance_armor;
		
		if (target == null || target.getItem().getType2() != L1Item.CATEGORY_ARMOR || target.isAccessory() 
				|| target.isIvoryTowerArmor(armorId) || target.isDragonT(armorId) ) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
			return false;
		}	
		if (safeEnchant < 0) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
			return false;
		}
		if (target.getBless() >= 128) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
			return false;
		}

		if (bless % 128 == 2) {
			if (enchantLevel > -safeEnchant) {
				successEnchant(pc, target, -1);
				pc.getInventory().saveItem(target, L1PcInventory.COL_ENCHANTLVL);
			} else {
				failureEnchant(pc, target);
				pc.getInventory().deleteItem(target);
			}
		} else if (enchantLevel < safeEnchant) {
			successEnchant(pc, target, randomLevel(target, bless));
			pc.getInventory().saveItem(target, L1PcInventory.COL_ENCHANTLVL);
		} else {
			enchantLevel = Math.abs(enchantLevel);
			int maxSetting = ItemSettingTable.getInstance().getEnchantMax(armorId);
			if ((maxSetting != -1) && (enchantLevel >= maxSetting)) {
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_19), true);
				return false;
			}
			if (enchantLevel >= Config.방어구인첸트) {
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_19), true);
				return false;
			}
			if (safeEnchant == 0) { 
				enchant_chance_armor = (int) (aa + ItemSettingTable.getInstance().getEnchantChance(target) * 100.0D);
			} else if (safeEnchant == 4) { 
				enchant_chance_armor = (int) (bb + ItemSettingTable.getInstance().getEnchantChance(target) * 100.0D);
			} else {
				enchant_chance_armor = (int) (cc + ItemSettingTable.getInstance().getEnchantChance(target) * 100.0D);
			}	
			enchant_chance_armor = (int) (enchant_chance_armor + ItemSettingTable.getInstance().getEnchantChance(target) * 100.0D);
			if (rnd <= enchant_chance_armor) {
				successEnchant(pc, target, randomLevel(target, bless));
				pc.getInventory().saveItem(target, L1PcInventory.COL_ENCHANTLVL);
			} else if ((enchantLevel >= 9) && (rnd < enchant_chance_armor * 2)) {
				pc.sendPackets(new S_ServerMessage(160, target.getLogName(), "$252", "$248"));
			} else {
				failureEnchant(pc, target);
				pc.getInventory().deleteItem(target);
			}

			if (pc.isGm()) {
				DecimalFormat format = new DecimalFormat(".##");
				pc.sendPackets(new S_SystemMessage("\\fT인챈트 - 레벨: " + enchantLevel + " / 성공확률: " + format.format(enchant_chance_armor / 100.0D)
						+ " >= 랜덤: " + format.format(rnd / 100.0D)));
			}
		}
		return true;
	}
	private boolean enchantDradonT(L1PcInstance pc, ItemInstance item, ItemInstance target) {
		int 블래스 = item.getBless(), 타겟아이템아이디 = target.getItem().getItemId(),
				인챈트레벨 = target.getEnchantLevel(), 안전인챈 = target.getItem().get_safeenchant(),
						rnd = _random.nextInt(10000) + 1,  
								aa = ArmorEnchantInformationTable.getInstance().getChance(-8, 인챈트레벨), 
								enchant_chance_armor = 0,  maxSetting = ItemSettingTable.getInstance().getEnchantMax(타겟아이템아이디);

		if (target == null || target.getItem().getType2() != L1Item.CATEGORY_ARMOR || !target.isDragonT(타겟아이템아이디)) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
			return false;
		}
		if (안전인챈 < 0) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
			return false;
		}
		if (target.getBless() >= 128) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
			return false;
		}
		if (블래스 % 128 == 2) {
			if (인챈트레벨 > -안전인챈) {
				successEnchant(pc, target, -1);
				pc.getInventory().saveItem(target, L1PcInventory.COL_ENCHANTLVL);
			} else {
				failureEnchant(pc, target);
				pc.getInventory().deleteItem(target);
			}
		} else if (인챈트레벨 < 안전인챈) {
			successEnchant(pc, target, randomLevel(target, 블래스));
			pc.getInventory().saveItem(target, L1PcInventory.COL_ENCHANTLVL);
		} else {
			인챈트레벨 = Math.abs(인챈트레벨);
			if ((maxSetting != -1) && (인챈트레벨 >= maxSetting)) {
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_19), true);
				return false;
			}
			if (인챈트레벨 >= Config.방어구인첸트) {
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_19), true);
				return false;
			}
			if (안전인챈 == 4) { 
				enchant_chance_armor = (int) (aa + ItemSettingTable.getInstance().getEnchantChance(target) * 100.0D);
			} 
			enchant_chance_armor = (int) (enchant_chance_armor + ItemSettingTable.getInstance().getEnchantChance(target) * 100.0D);
			if (rnd <= enchant_chance_armor) {
				successEnchant(pc, target, randomLevel(target, 블래스));
				pc.getInventory().saveItem(target, L1PcInventory.COL_ENCHANTLVL);
			} else if ((인챈트레벨 >= 9) && (rnd < enchant_chance_armor * 2)) {
				pc.sendPackets(new S_ServerMessage(160, target.getLogName(), "$252", "$248"));
			} else {
				failureEnchant(pc, target);
				pc.getInventory().deleteItem(target);
			}

			if (pc.isGm()) {
				DecimalFormat format = new DecimalFormat(".##");
				pc.sendPackets(new S_SystemMessage("\\fT인챈트 - 레벨: " + 인챈트레벨 + " / 성공확률: " + format.format(enchant_chance_armor / 100.0D)
						+ " >= 랜덤: " + format.format(rnd / 100.0D)));
			}
		}
		return true;
	}
	private boolean enchantSnapper(L1PcInstance pc, ItemInstance item, ItemInstance target) {
		int 아이템아이디 = item.getItemId(), 타겟아이템아이디 = target.getItem().getItemId(),
				인챈트레벨 = target.getEnchantLevel(), 안전인챈 = target.getItem().get_safeenchant(),
						rnd = _random.nextInt(10000) + 1,  
						aa = AccessoryEnchantInformationTable.getInstance().getChance(-6,인챈트레벨), 
						enchant_chance_accessory = 0,  maxSetting = ItemSettingTable.getInstance().getEnchantMax(타겟아이템아이디);

		if (target == null || target.getItem().getType2() != L1Item.CATEGORY_ARMOR || !target.isSnapper(타겟아이템아이디)) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
			return false;
		}		
		if (안전인챈 < 0) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
			return false;
		}
		if (target.getBless() >= 128) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
			return false;
		}
		if (인챈트레벨 >= Config.스냅퍼_악세사리_인챈트 || (maxSetting != -1 && 인챈트레벨 >= maxSetting)) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_19), true);
			return false;
		}	
		if (아이템아이디 == ItemId.스냅퍼_강화_주문서) {
			enchant_chance_accessory = (int) (aa + ItemSettingTable.getInstance().getEnchantChance(target) * 100.0D);
		}
		enchant_chance_accessory = (int) (enchant_chance_accessory + ItemSettingTable.getInstance().getEnchantChance(target) * 100.0D);

		if (rnd <= enchant_chance_accessory) {
			successEnchant(pc, target, 1);
			pc.getInventory().saveItem(target, L1PcInventory.COL_ENCHANTLVL);
		} else {
			failureEnchant(pc, target);
			pc.getInventory().deleteItem(target);
		}

		if (pc.isGm()) {
			DecimalFormat format = new DecimalFormat(".##");
			pc.sendPackets(new S_SystemMessage("\\fT인챈트 - 레벨: " + 인챈트레벨 + " / 성공확률: " + format.format(enchant_chance_accessory / 100.0D)
					+ " >= 랜덤: " + format.format(rnd / 100.0D)));
		}
		return true;
	}
	private boolean enchantRoomtis(L1PcInstance pc, ItemInstance item, ItemInstance target) {
		int 아이템아이디 = item.getItemId(), 타겟아이템아이디 = target.getItem().getItemId(),
				인챈트레벨 = target.getEnchantLevel(), 안전인챈 = target.getItem().get_safeenchant(),
						rnd = _random.nextInt(10000) + 1,  
						aa = AccessoryEnchantInformationTable.getInstance().getChance(-5, 인챈트레벨), 
						enchant_chance_accessory = 0,  maxSetting = ItemSettingTable.getInstance().getEnchantMax(타겟아이템아이디);

		if (target == null || target.getItem().getType2() != L1Item.CATEGORY_ARMOR || !target.isRoontis(타겟아이템아이디)) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
			return false;
		}
		
		if (안전인챈 < 0) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
			return false;
		}
		if (target.getBless() >= 128) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
			return false;
		}
		if (인챈트레벨 >= Config.룸티스_악세사리_인챈트 || (maxSetting != -1 && 인챈트레벨 >= maxSetting)) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_19), true);
			return false;
		}	
		if (아이템아이디 == ItemId.룸티스_강화_주문서) {
			enchant_chance_accessory = (int) (aa + ItemSettingTable.getInstance().getEnchantChance(target) * 100.0D);
		}
		enchant_chance_accessory = (int) (enchant_chance_accessory + ItemSettingTable.getInstance().getEnchantChance(target) * 100.0D);

		if (rnd <= enchant_chance_accessory) {
			successEnchant(pc, target, 1);
			pc.getInventory().saveItem(target, L1PcInventory.COL_ENCHANTLVL);
		} else {
			failureEnchant(pc, target);
			pc.getInventory().deleteItem(target);
		}

		if (pc.isGm()) {
			DecimalFormat format = new DecimalFormat(".##");
			pc.sendPackets(new S_SystemMessage("\\fT인챈트 - 레벨: " + 인챈트레벨 + " / 성공확률: " + format.format(enchant_chance_accessory / 100.0D)
					+ " >= 랜덤: " + format.format(rnd / 100.0D)));
		}
		return true;
	}
	private boolean enchantAccessory(L1PcInstance pc, ItemInstance item, ItemInstance target) {
		int 아이템아이디 = item.getItemId(), 블래스 = item.getBless(), 타겟아이템아이디 = target.getItem().getItemId(),
				인챈트레벨 = target.getEnchantLevel(), 안전인챈 = target.getItem().get_safeenchant(),
						rnd = _random.nextInt(10000) + 1,  
						aa = AccessoryEnchantInformationTable.getInstance().getChance(-1,인챈트레벨), 
						bb = AccessoryEnchantInformationTable.getInstance().getChance(-2,인챈트레벨),
						cc = AccessoryEnchantInformationTable.getInstance().getChance(-3,인챈트레벨),
						enchant_chance_accessory = 0,  maxSetting = ItemSettingTable.getInstance().getEnchantMax(타겟아이템아이디);
		if (target == null || target.getItem().getType2() != L1Item.CATEGORY_ARMOR || !target.isAccessory()) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
			return false;
		}
		if(target.cashItems(타겟아이템아이디)) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
			return false;
		}	
		if (안전인챈 < 0) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
			return false;
		}
		if (target.getBless() >= 128) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
			return false;
		}
		if (인챈트레벨 >= Config.악세사리 || (maxSetting != -1 && 인챈트레벨 >= maxSetting)) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_19), true);
			return false;
		}	
		if (아이템아이디 != ItemId.오림_장신구_마법_주문서 && 아이템아이디 != ItemId.축복_오림_장신구_마법_주문서 && 안전인챈 == 0) {  
			enchant_chance_accessory = (int) (aa + ItemSettingTable.getInstance().getEnchantChance(target) * 100.0D);
		} else if (아이템아이디 == ItemId.오림_장신구_마법_주문서) {
			enchant_chance_accessory = (int) (bb + ItemSettingTable.getInstance().getEnchantChance(target) * 100.0D);
		} else if (아이템아이디 == ItemId.축복_오림_장신구_마법_주문서) {
			enchant_chance_accessory = (int) (cc + ItemSettingTable.getInstance().getEnchantChance(target) * 100.0D);
		}
		enchant_chance_accessory = (int) (enchant_chance_accessory + ItemSettingTable.getInstance().getEnchantChance(target) * 100.0D);

		if (rnd <= enchant_chance_accessory) {
			successEnchant(pc, target, 1);
			pc.getInventory().saveItem(target, L1PcInventory.COL_ENCHANTLVL);
		} else if (아이템아이디 == ItemId.오림_장신구_마법_주문서 || 아이템아이디 == ItemId.축복_오림_장신구_마법_주문서) {
			if (인챈트레벨 > 0 && 블래스 % 128 != 0 && _random.nextInt(100) < 100 - 인챈트레벨) {
				successEnchant(pc, target, -1);
				pc.getInventory().saveItem(target, L1PcInventory.COL_ENCHANTLVL);
			}
			pc.sendPackets(new S_ServerMessage(4056, target.getLogName()), true);
		} else {
			failureEnchant(pc, target);
			pc.getInventory().deleteItem(target);
		}
		if (pc.isGm()) {
			DecimalFormat format = new DecimalFormat(".##");
			pc.sendPackets(new S_SystemMessage("\\fT인챈트 - 레벨: " + 인챈트레벨 + " / 성공확률: " + format.format(enchant_chance_accessory / 100.0D)
					+ " >= 랜덤: " + format.format(rnd / 100.0D)));
		}
		return true;
	}
	private boolean enchantAttrWeapon(L1PcInstance pc, ItemInstance item, ItemInstance target) {
		int 아이템아이디 = item.getItemId(), 속성레벨 = target.getAttrEnchantLevel(), 확률_1단계 = Config.ATTR_ENCHANT_CHANCE, 
				확률_2단계 = Config.ATTR_ENCHANT_CHANCE_TWO, 확률_3단계 = Config.ATTR_ENCHANT_CHANCE_TREE;

		if (target.getItem().getType2() != L1Item.CATEGORY_WEAPON) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
			return false;
		}
		if (속성레벨 > 0) {
			if (아이템아이디 == ItemId.불_무기_강화_주문서 && !(속성레벨 >= 1 && 속성레벨 <= 3)) { 
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_16), true);
				return false;
			}
			if (아이템아이디 == ItemId.물_무기_강화_주문서 && !(속성레벨 >= 4 && 속성레벨 <= 6)) { 
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_16), true);
				return false;
			}
			if (아이템아이디 == ItemId.바람_무기_강화_주문서 && !(속성레벨 >= 7 && 속성레벨 <= 9)) { 
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_16), true);
				return false;
			}
			if (아이템아이디 == ItemId.지령_무기_강화_주문서 && !(속성레벨 >= 10 && 속성레벨 <= 12)) {
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_16), true);
				return false;
			}
		} 
		if (아이템아이디 == ItemId.불_무기_강화_주문서) {
			if (속성레벨 == 0 || (속성레벨 >= 4)) {
				if (CommonUtil.randomRange(1, 100) < 확률_1단계) {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_17, target.getLogName()), true);
					target.setAttrEnchantLevel(1);
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_18, target.getLogName()), true);
				}
			} else if (속성레벨 == 1) {
				if (CommonUtil.randomRange(1, 100) < 확률_2단계) {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_17, target.getLogName()), true);
					target.setAttrEnchantLevel(2);
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_18, target.getLogName()), true);
				}
			} else if (속성레벨 == 2) {
				if (CommonUtil.randomRange(1, 100) < 확률_3단계) {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_17, target.getLogName()), true);
					target.setAttrEnchantLevel(3);
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_18, target.getLogName()), true);
				}
			} else if (속성레벨 == 3) {
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
				return false;
			}
		}
		if (아이템아이디 == ItemId.물_무기_강화_주문서) {
			if ((속성레벨 >= 0 && 속성레벨 <= 3) || 속성레벨 >= 7) {
				if (CommonUtil.randomRange(1, 100) < 확률_1단계) {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_17, target.getLogName()), true);
					target.setAttrEnchantLevel(4);
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_18, target.getLogName()), true);
				}
			} else if (속성레벨 == 4) {
				if (CommonUtil.randomRange(1, 100) < 확률_2단계) {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_17, target.getLogName()), true);
					target.setAttrEnchantLevel(5);
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_18, target.getLogName()), true);
				}
			} else if (속성레벨 == 5) {
				if (CommonUtil.randomRange(1, 100) < 확률_3단계) {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_17, target.getLogName()), true);
					target.setAttrEnchantLevel(6);
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_18, target.getLogName()), true);
				}
			} else if (속성레벨 == 6) {
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
				return false;
			}
		}
		if (아이템아이디 == ItemId.바람_무기_강화_주문서) {
			if ((속성레벨 >= 0 && 속성레벨 <= 6) || 속성레벨 >= 10) {
				if (CommonUtil.randomRange(1, 100) < 확률_1단계) {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_17, target.getLogName()), true);
					target.setAttrEnchantLevel(7);
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_18, target.getLogName()), true);
				}
			} else if (속성레벨 == 7) {
				if (CommonUtil.randomRange(1, 100) < 확률_2단계) {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_17, target.getLogName()), true);
					target.setAttrEnchantLevel(8);
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_18, target.getLogName()), true);
				}
			} else if (속성레벨 == 8) {
				if (CommonUtil.randomRange(1, 100) < 확률_3단계) {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_17, target.getLogName()), true);
					target.setAttrEnchantLevel(9);
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_18, target.getLogName()), true);
				}
			} else if (속성레벨 == 9) {
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
				return false;
			}
		}
		if (아이템아이디 == ItemId.지령_무기_강화_주문서) {
			if (속성레벨 >= 0 && 속성레벨 <= 9) {
				if (CommonUtil.randomRange(1, 100) < 확률_1단계) {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_17, target.getLogName()), true);
					target.setAttrEnchantLevel(10);
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_18, target.getLogName()), true);
				}
			} else if (속성레벨 == 10) {
				if (CommonUtil.randomRange(1, 100) < 확률_2단계) {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_17, target.getLogName()), true);
					target.setAttrEnchantLevel(11);
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_18, target.getLogName()), true);
				}
			} else if (속성레벨 == 11) {
				if (CommonUtil.randomRange(1, 100) < 확률_3단계) {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_17, target.getLogName()), true);
					target.setAttrEnchantLevel(12);
				} else {
					pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_18, target.getLogName()), true);
				}
			} else if (속성레벨 == 12) {
				pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1), true);
				return false;
			}
		}
		pc.getInventory().updateItem(target, L1PcInventory.COL_ATTRENCHANTLVL);
		pc.getInventory().saveItem(target, L1PcInventory.COL_ATTRENCHANTLVL);
		return true;
	}
	public static void successEnchant(L1PcInstance pc, ItemInstance target, int i) {
		String sa = "";
		String sb = "";
		int 안전인챈 = target.getItem().get_safeenchant(), 기존인챈트 = target.getEnchantLevel(),
				새로운인챈트 = 기존인챈트 + i, 타입 = target.getItem().getType2();
		if (타입 == L1Item.CATEGORY_WEAPON) {
			if ((!target.isIdentified()) || (target.getEnchantLevel() == 0))
				switch (i) {
				case -1: sa = "$246"; sb = "$247"; break;
				case 1: sa = "$245"; sb = "$247"; break;
				case 2: sa = "$245"; sb = "$248"; break;
				case 3: sa = "$245"; sb = "$248";
				case 0:
				}
			else
				switch (i) {
				case -1: sa = "$246"; sb = "$247"; break;
				case 1: sa = "$245"; sb = "$247"; break;
				case 2: sa = "$245"; sb = "$248"; break;
				case 3: sa = "$245"; sb = "$248";
				case 0:
				}
		} else if (타입 == L1Item.CATEGORY_ARMOR) {
			if ((!target.isIdentified()) || (target.getEnchantLevel() == 0))
				switch (i) {
				case -1: sa = "$246"; sb = "$247"; break;
				case 1: sa = "$252"; sb = "$247"; break;
				case 2: sa = "$252"; sb = "$248"; break;
				case 3: sa = "$252"; sb = "$248";
				case 0:
				}
			else {
				switch (i) {
				case -1: sa = "$246"; sb = "$247"; break;
				case 1: sa = "$252"; sb = "$247"; break;
				case 2: sa = "$252"; sb = "$248"; break;
				case 3: sa = "$252"; sb = "$248";
				case 0:
				}
			}
		}
		pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_20, target.getLogName(), sa, sb));

		if (target.isEquipped()) {
			pc.getEquipSlot().remove(target);
			target.setEnchantLevel(새로운인챈트);
			pc.getEquipSlot().set(target);
		} else {
			target.setEnchantLevel(새로운인챈트);
		}
		pc.sendPackets(new S_OwnCharStatus(pc), true);
		
		if ((i > 0) && (target.getBless() % 128 == 2)) {
			target.setBless(target.getBless() - 1);
			pc.getInventory().updateItem(target, L1PcInventory.COL_BLESS);
		} 
		pc.getInventory().updateItem(target, L1PcInventory.COL_ENCHANTLVL);

		if (새로운인챈트 > 안전인챈) {
			pc.getInventory().saveItem(target, L1PcInventory.COL_ENCHANTLVL);
		}		
		if (ItemMessageTable.getInstance().isEnchantMessage(target.getItemId())) { 
			L1ItemMessage temp = ItemMessageTable.getInstance().getEnchantMessage(target.getItemId());
			if (temp != null) {
				if (temp.getType() == 2 && 새로운인챈트 >= temp.getOption()) {
					if (temp.getMent() == null || temp.getMent().equals("")) {
						String itemName = target.getViewName();
						if (itemName == null) {
							itemName = target.getName();
							String message = String.format("누군가가 +" + 새로운인챈트 + "" + target.getName() + " 강화를 성공 하였습니다.");
							L1World.getInstance().broadcastPacketToAll(new S_SystemMessage(message)); 
						}
					} else {
						L1World.getInstance().broadcastPacketToAll(new S_SystemMessage(temp.getMent()));
						L1World.getInstance().broadcastPacketToAll(new S_PacketBox(S_PacketBox.GREEN_MESSAGE, temp.getMent()));					
					}
				}
				SJManager.getInstance().EnchantAppend(target.getName(), 기존인챈트, 새로운인챈트, pc.getName(), 0);
			}
		}
		
		/** 매니저 로그 */
		if (타입 == L1Item.CATEGORY_WEAPON) {
			if (새로운인챈트 >= Config.WORLD_MSG_ENCHANT_WEAPON) {
				L1World.getInstance().broadcastPacketToAll(
						new S_PacketBox(S_PacketBox.GREEN_MESSAGE, "누군가가 +" + 새로운인챈트 + " " + target.getName() + " 강화를 성공 하였습니다."));
			}
			SJManager.getInstance().EnchantAppend(target.getName(), 기존인챈트, 새로운인챈트, pc.getName(), 0);
			
			if(새로운인챈트 >= 5) {
				SJManager.getInstance().EnchantAppend(target.getName(), 기존인챈트, 새로운인챈트, pc.getName(), 0);
			} else if(새로운인챈트 < 0) {
				SJManager.getInstance().EnchantAppend(target.getName(), 기존인챈트, 새로운인챈트, pc.getName(), 0);
			}
			if (새로운인챈트 >= Config.무기인첸트) {
				SJManager.getInstance().EnchantAppend(target.getName(), 기존인챈트, 새로운인챈트, pc.getName(), 0);
				 
			}
		}

		if (타입 == L1Item.CATEGORY_ARMOR) {
			if (target.getItem().getType() >= 8 && target.getItem().getType() <= 12) {
				if (새로운인챈트 >= Config.WORLD_MSG_ENCHANT_ACCESS) {
					L1World.getInstance().broadcastPacketToAll(new S_PacketBox(S_PacketBox.GREEN_MESSAGE,
							"누군가가 +" + 새로운인챈트 + " " + target.getName() + " 강화를 성공 하였습니다."));
				}
			} else if (새로운인챈트 >= Config.WORLD_MSG_ENCHANT_ARMOR) {
				L1World.getInstance().broadcastPacketToAll(
						new S_PacketBox(S_PacketBox.GREEN_MESSAGE, "누군가가 +" + 새로운인챈트 + " " + target.getName() + " 강화를 성공 하였습니다."));
			}
			SJManager.getInstance().EnchantAppend(target.getName(), 기존인챈트, 새로운인챈트, pc.getName(), 0);
			 
			if(새로운인챈트 >= 5) {
				SJManager.getInstance().EnchantAppend(target.getName(), 기존인챈트, 새로운인챈트, pc.getName(), 0);
			} else if(새로운인챈트 < 0) {
				SJManager.getInstance().EnchantAppend(target.getName(), 기존인챈트, 새로운인챈트, pc.getName(), 0);
			}
			if (새로운인챈트 >= Config.방어구인첸트) {
				SJManager.getInstance().EnchantAppend(target.getName(), 기존인챈트, 새로운인챈트, pc.getName(), 0);
				 
			}
		}
	}
	private void failureEnchant(L1PcInstance pc, ItemInstance target) {
		String sa = "";
		int itemType = target.getItem().getType2();

		if (itemType == 1) {
			if ((!target.isIdentified()) || (target.getEnchantLevel() == 0)) {
				sa = " $245";
			} else
				sa = " $245";
		} else if (itemType == 2) {
			if ((!target.isIdentified()) || (target.getEnchantLevel() == 0)) {
				sa = " $252";
			} else {
				sa = " $252";
			}
		}
		
		SJManager.getInstance().EnchantAppend(target.getName(), target.getEnchantLevel(), 0, pc.getName(), 1);
 

		pc.sendPackets(new S_ServerMessage(164, target.getLogName(), sa));
	}
	private int randomLevel(ItemInstance item, int bless) {
		int random = 0, 아이텝타입 = item.getItem().getType2(), 안전인챈 = item.getItem().get_safeenchant(), itemId = item.getItemId(), enchantLevel = item.getEnchantLevel(), material = item.getItem().getMaterial();
		
		bless %= 128;

		if (bless == 0) {
			if ((아이텝타입 == L1Item.CATEGORY_ARMOR) 
					&& ((material == L1Item.TYPE_MATARIAL_BONE)
					|| (material == L1Item.TYPE_MATARIAL_BLACKMITHRIL))) {
				random = _random.nextInt(100) < 42 ? 2 : 1;
			}
			else if ((enchantLevel <= 3) && ((itemId == ItemId.바람칼날_단검) 
					|| (itemId == ItemId.진명황_집행검) || (itemId == ItemId.붉은_그림자_이도류) 
					|| (itemId == ItemId.수정결정체_지팡이) || (itemId == ItemId.야수왕_크로우))) {
				random = _random.nextInt(100) < 22 ? 3 : 1;
			} 
			else if ((enchantLevel <= 4) && ((itemId == ItemId.바람칼날_단검) 
					|| (itemId == ItemId.진명황_집행검) || (itemId == ItemId.붉은_그림자_이도류) 
					|| (itemId == ItemId.수정결정체_지팡이) || (itemId == ItemId.야수왕_크로우))) {
				random = _random.nextInt(100) < 42 ? 2 : 1;
			} 
			else if (enchantLevel <= 2) {
				random = 1 + _random.nextInt(3);
			}
			else if (enchantLevel <= 4) {
				random = 1 + _random.nextInt(2);
			}
			else if (enchantLevel <= 5) {
				random = _random.nextInt(100) < 42 ? 2 : 1;
			}			
			else {
				random = 1;
			}			
		} else if (bless == 1) {
			random = 1;
		}
		else if (bless == 2) {
			random = -1;
		}
		if (아이텝타입 == L1Item.CATEGORY_WEAPON) {
			if (안전인챈 == 0) {
				if (enchantLevel + random >= Config.무기인첸트) {
					random = Config.무기인첸트 - enchantLevel;
				}
			} else if (enchantLevel + random >= Config.무기인첸트) {
				random = Config.무기인첸트 - enchantLevel;
			}
		} else if (아이텝타입 == L1Item.CATEGORY_ARMOR) {
			if (!item.isAccessory()) {
				if (안전인챈 == 0) {
					if (enchantLevel + random >= Config.방어구인첸트) {
						random = Config.방어구인첸트 - enchantLevel;
					}
				} else if (enchantLevel + random >= Config.방어구인첸트) {
					random = Config.방어구인첸트 - enchantLevel;
				}

			} else if (enchantLevel + random >= Config.악세사리) {
				random = Config.악세사리 - enchantLevel;
			}
		}
		return random;
	}
}